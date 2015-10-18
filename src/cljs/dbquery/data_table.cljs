(ns dbquery.data-table
  (:require [dbquery.commons :as c]
            [clojure.string :as s]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn column-toolbar [show?]
  [:div {:style {:position "absolute" :float "right"
                 :display (if @show? "" "none")}}
   [c/button-group {:bsSize "xsmall"}
    [c/button [:i.fa.fa-filter]]
    [c/button [:i.fa.fa-minus]]
    [c/button [:i.fa.fa-plus]]]])

(defn data-table [data sort-fn refresh-fn]
  (let [sort-state (atom [])
        sort-icons (atom {})
        roll-sort (fn[i]
                    (let [j (inc i)
                          next-icon
                          (-> (case (@sort-icons i "fa-sort")
                                "fa-sort" ["fa-sort-up" (swap! sort-state conj j)]
                                "fa-sort-up" ["fa-sort-down" (swap! sort-state (partial replace {j (- j)}))]
                                "fa-sort-down" ["fa-sort" (swap! sort-state (partial remove #(= % (- j))))]) first)]
                      (sort-fn @sort-state)
                      (swap! sort-icons assoc i next-icon)))]
    (fn [data sort-fn refresh-fn]
      [:div.table-responsive
       {:style {:overflow-y "scroll" :height "100%"}}
       [:table.table {:class "table-hover table-bordered summary"}
        [:thead
         [:tr [:th {:style {:width "1px" :padding-left "2px" :padding-right "2px"}}
               [c/split-button {:style {:display "flex"}
                                :title (r/as-element [:i.fa.fa-refresh])
                                :on-click refresh-fn :bsSize "xsmall"}
                [c/menu-item {:eventKey 1} "Join with ..."]]]
          (doall
           (map-indexed
            (fn[i c]
              (let [show? (atom false)]
                ^{:key i}
                [:th
                 [:a.btn-link {:on-click #(swap! show? not)} c]
                 [:a.btn-link {:on-click #(roll-sort i)}
                  [:i.fa.btn-sort {:class (@sort-icons i "fa-sort")}]]
                 [column-toolbar show?]])) (@data "columns")))]]
        [:tbody
         (map-indexed
          (fn [i row]
            ^{:key i}[:tr [:td (inc i)]
                      (map-indexed
                       (fn[j v] ^{:key j}[:td v]) row)]) (@data "rows"))]
        [:tfoot
         [:tr [:td {:col-span (count (@data "columns"))}
               [c/button "More"]]]]]])))

(defn execute-query [ds query data-atom error-atom]
  (POST (str "/ds/" (ds "id") "/exec-sql")
        :params query :response-format :json :format :json
        :handler #(reset! data-atom (% "data"))
        :error-handler #(reset! error-atom %)))

(defn query-table [ds query]
  (let [data (atom {})
        error (atom nil)
        cur-query (atom query)
        refresh-fn #(execute-query ds @cur-query data error)
        sort-data-fn
        (fn[sort-state]
          (let [order-by
                (if-not (empty? sort-state)
                  (->> sort-state
                       (map #(if (neg? %) (str (- %) " desc") %))
                       (s/join ",")
                       (str " order by ")))]
            (reset! cur-query (update query :raw-sql #(-> % (s/replace #"(?im)\s+order\s+by\+.+$" "") (str order-by))))
            (refresh-fn)))]
    (refresh-fn)
    (fn[ds query] [data-table data sort-data-fn refresh-fn])))
