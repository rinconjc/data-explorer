(ns dbquery.data-table
  (:require [dbquery.commons :as c]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn data-table [data]
  [:div.table-responsive
   [:table.table {:class "table-hover"}
    [:thead
     [:tr
      (for [c (@data "columns")]
        [:th c [:i.fa {:class "fa-sort"}]])]]
    [:tbody
     (for [row (@data "rows")]
       [:tr
        (for [v row]
          [:td v])])]
    [:tfoot
     [:tr
      [:td {:colspan (-> @data "columns" count)}
       [c/button "More"]]]]]])

(defn query-table [ds query]
  (let [data (atom [])
        error (atom nil)]
    (POST (str "/ds/" (ds "id") "/exec-sql")
          :params query
          :handler #(reset! data (% "data"))
          :error-handler #(reset! error %))
    (fn[] [data-table data])))
