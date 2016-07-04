(ns dbquery.sql-utils
  (:require [clojure.string :as s]
            [clojure.string :as str]))

(def ^:const sort-icons {:up "fa-sort-up" :down "fa-sort-down" nil "fa-sort"})
(def ^:const next-order {nil :up :up :down :down nil})

(def ^:private blanks #{\newline \return \space \tab \formfeed})

(defn set-order [order col-index next-order]
  (if-let [[[i [_ ord]]] (seq (keep-indexed #(if (= col-index (first %2)) [%1 %2]) order))]
    (let [ord* (next-order ord)]
      (if (nil? ord*)
        (into [] (keep-indexed #(if (not= %1 i) %2) order))
        (update order i assoc 1 ord*)))
    (conj order [col-index (next-order nil)])))

(defn order-by [order-state]
  (if-not (empty? order-state)
    (->> (for [[c ord] order-state]
          (str (inc c) (if (= :up ord) "" " desc")))
        (str/join ",")
        (str " order by "))))

(defn sql-where [conditions]
  (if-not (empty? conditions)
    (str " WHERE " (s/join " and " (for [[col {:keys [op value]}] conditions]
                                     (str col " " op " '" value "'"))))))

(defn from-sql [tables]
  (str " FROM " (first tables) " "
       (->> tables rest (map #(cond
                                (string? %) (str "," %)
                                (map? %) (let[{:keys[join to on]} %]
                                           (str join " JOIN " to " on " on))))
            (s/join " "))))

(defn sql-select [{:keys[cols tables conditions order groups]}]
  (str "SELECT " (s/join "," cols) (from-sql tables) (sql-where conditions) (order-by order)))

(defn sql-distinct [query col]
  (sql-select (assoc query :cols [(str "distinct " col)] :order [])))

(defn query-from-sql [raw-sql]
  ;; (SqlQuery. (atom ["*"]) (atom [(str "(" raw-sql ")")]) (atom {}) (atom []) (atom []))
  {:cols ["*"] :tables [(str "(" raw-sql ")")] :conditions {} :order [] :group []})

(defn query-from-table [table]
  ;; (SqlQuery. (atom ["*"]) (atom [table]) (atom {}) (atom []) (atom []))
  {:cols ["*"] :tables [table] :conditions {} :order [] :group []})

;; sql parsing

(defn inline-comment [text i]
  (if (= "--" (subs text i (+ i 2)))
    (let [end (.indexOf text "\n" i)]
      (if (>= end 0) (inc end) (count text)))))

(defn block-comment [text i]
  (if (= "/*" (subs text i (+ i 2)))
    (let [end (.indexOf text "*/" i)]
      (if (>= end 0) (+ end 2)
          #?(:clj (throw (Exception. "Unclosed block comment"))
             :cljs (throw (js/Error. "Unclosed block comment")))))))

(defn skip-comments [text i]
  (if-let [offset (or (and (< (+ i 2) (count text))
                           (or (inline-comment text i) (block-comment text i)))
                      (and (< i (count text)) (blanks (.charAt text i))
                           (loop [j (inc i)]
                             (if (blanks (.charAt text j)) (recur (inc j)) j))))]
    (skip-comments text offset)
    i))

(defn end-of-block [text i]
  (if-let [[_ begin] (re-find #"(?i)^\s*(DECLARE|BEGIN)\W" (subs text i))]
    (let [offset (+ i (count begin))
          sub-text (subs text offset)
          open (if (= (s/upper-case begin) "BEGIN") 1 0)]
      #?(:cljs (loop [open open
                      regex (js/RegExp. "(begin|end)\\W", "gi")]
                 (let [[_ [match] index] (.exec regex sub-text)
                       open (if (= (s/lower-case match) "end") (dec open) (inc open))]
                   (if (> open 0)
                     (recur open regex)
                     (+ 1 offset (.indexOf sub-text ";" index)))))
         :clj (loop [matcher (re-matcher #"(?i)(begin|end)\W" sub-text)
                     open open]
                (when (.find matcher)
                  (let [open (if (.equalsIgnoreCase "end" (.group matcher 1)) (dec open) (inc open))]
                    (if (> open 0)
                      (recur matcher open)
                      (+ 1 offset (.indexOf sub-text ";" (.end matcher)))))))))))

(defn end-of-stmt [text offset]
  (let [pos (skip-comments text offset)]
    (if (or (>= pos (count text)) (= \; (.charAt text pos)))
      pos
      (end-of-stmt text (inc pos)))))

(defn next-stmt [text offset]
  (if (< offset (count text))
    (let [start (skip-comments text offset)
          end (or (end-of-block text start) (end-of-stmt text start))]
      (if (and (> end start) (<= end (count text)))
        [(subs text start end) (inc end)]))))

(defn sql-statements [text]
  (loop [start 0
         stmts []]
    (let [[sql offset] (next-stmt text start)]
      (if sql (recur offset (conj stmts sql))
          stmts))))
