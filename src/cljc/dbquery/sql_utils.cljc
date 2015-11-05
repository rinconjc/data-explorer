(ns dbquery.sql-utils
  (:require [clojure.string :as s]))


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
