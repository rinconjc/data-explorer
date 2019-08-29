(ns dbquery.sql-utils
  (:require [clojure.string :as s]
            [clojure.string :as str]))

(def sort-icons {:up "fa-sort-up" :down "fa-sort-down" nil "fa-sort"})
(def next-order {nil :up :up :down :down nil})

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
         (s/join ",")
        (str " order by "))))

(defn sql-where [conditions]
  (if-not (empty? conditions)
    (str " WHERE " (s/join " and " (for [[col predicate] conditions]
                                     (str col " " predicate))))))

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
  {:cols ["*"] :tables [(str "(" raw-sql ") t")] :conditions {} :order [] :group []})

(defn query-from-table [table]
  ;; (SqlQuery. (atom ["*"]) (atom [table]) (atom {}) (atom []) (atom []))
  {:cols ["*"] :tables [table] :conditions {} :order [] :group []})

;; sql parsing

(defn sql-statements
  ([text] (sql-statements text (cond (re-find #"/\s*(\n|$)" text) "/" :else ";")))
  ([text sep]
   (->> (str/split text (re-pattern (str sep "\\s*(\\n|$)")))
        (map str/trim)
        (filter (complement str/blank?)))))
