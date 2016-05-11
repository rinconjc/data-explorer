(ns dbquery.sql-utils
  (:require [clojure.string :as s]
            [clojure.string :as str]))


(defn order-by [order-state]
  (if-not (empty? order-state)
    (->> order-state
         (map #(if (neg? %) (str (- %) " desc") %))
         (s/join ",")
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

(defn sql-select [cols tables conditions order groups]
  (str "SELECT " (s/join "," cols) (from-sql tables) (sql-where conditions) (order-by order)))

;; sql parsing

(defn inline-comment [text i]
  (if (= "--" (.substring text i (+ i 2)))
    (inc (.indexOf text "\n" i))))

(defn block-comment [text i]
  (if (= "/*" (.substring text i (+ i 2)))
    (+ (.indexOf text "*/" i) 2)))

(defn skip-comments [text i]
  (if-let [offset (and (< (+ i 2) (count text)) (or (inline-comment text i) (block-comment text i)))]
    (skip-comments text offset)
    i))

(defn end-of-stmt [text offset]
  (let [pos (skip-comments text offset)]
    (if (or (>= pos (.length text)) (= ";" (.charAt text pos)))
      pos
      (end-of-stmt text (inc pos)))))

(defn next-stmt [text offset]
  (if (< offset (count text))
    (let [start (skip-comments text offset)
          end (end-of-stmt text start)]
      (if (and (> end start) (<= end (count text)))
        [(.substring text start end) (inc end)]))))

(defn sql-statements [text]
  (loop [start 0
         stmts []]
    (let [[sql offset] (next-stmt text start)]
      (if sql (recur offset (conj stmts sql))
          stmts))))
