(ns dbquery.data-table
  (:require [dbquery.commons :as c]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn data-table [data]
  (let [sort-state (atom [])
        sort-icons (atom {})
        roll-sort (fn[i]
                    (let [next-icon
                          (-> (case (@sort-icons i "fa-sort")
                             "fa-sort" ["fa-sort-up" (swap! sort-state conj i)]
                             "fa-sort-up" ["fa-sort-down" (swap! sort-state (partial replace {i (- i)}))]
                             "fa-sort-down" ["fa-sort" (swap! sort-state (partial remove #(= (- i))))]) first)]
                      (swap! sort-icons assoc i next-icon)))]
    (fn [data]
      [:div.table-responsive
       {:style {:overflow-y "scroll" :height "100%"}}
       [:table.table {:class "table-hover table-bordered summary"}
        [:thead
         [:tr [:th {:style {:width "1px"}}]
          (doall (map-indexed
            (fn[i c] ^{:key i}
              [:th c [:a {:class "btn-link" :on-click #(roll-sort i)}
                      [:i.fa.btn-sort {:class (@sort-icons i "fa-sort")}]]]) (@data "columns")))]]
        [:tbody
         (map-indexed
          (fn [i row]
            ^{:key i}[:tr [:td (inc i)]
                      (map-indexed
                       (fn[j v] ^{:key j}[:td v]) row)]) (@data "rows"))]
        [:tfoot
         [:tr [:td {:col-span (count (@data "columns"))}
               [c/button "More"]]]]]])))

(defn query-table [ds query]
  (let [data (atom {})
        error (atom nil)]
    (POST (str "/ds/" (ds "id") "/exec-sql")
          :params query :response-format :json :format :json
          :handler #(reset! data (% "data"))
          :error-handler #(reset! error %))
    (fn[ds query] [data-table data])))
