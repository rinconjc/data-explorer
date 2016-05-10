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

(defn in-line-comment [text i]
  (if (= "--" (.substring text i (+ i 2)))
    (inc (.indexOf text \n i))))

(defn block-comment [text i]
  (if (= "/*" (.substring text i (+ i 2)))
    (+ (.indexOf text "*/" i) 2)))

(defn skip-comments [text i]
  (if-let [offset (or (in-line-comment text offset) (block-comment text i))]
    (skip-comments text offset)
    i))

(defn non-blank [text sql-start i]
  (if (and (= sql-start i) (= ' ' (.charAt text i)))
    [nil (inc i) (inc i)]
    [nil sql-start (inc i)]))

(defn parse-end-sql [text offset]
  (let [pos (skip-comments text offset)]
    (if (or (>= pos (.length text)) (= ";" (.charAt text pos)))
      pos
      (parse-end-sql text (inc pos)))))

(defn next-sql [text offset]
  (let [start (skip-comments text offset)
        end (parse-end-sql text start)]
    (if (&& (> end start) (<= end (count text)))
      [(.substring text start end) (inc end)])))

(defn parse-sql [text]
  (loop [start 0
         stmts []]
    (let [[sql offset] (next-sql start)]
      (if sql (recur offset (conj stmts sql)))
      stmts)))
