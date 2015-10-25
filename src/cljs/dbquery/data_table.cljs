(ns dbquery.data-table
  (:require [dbquery.commons :as c]
            [clojure.string :as s]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(deftype SortControl [sort-state sort-icons sorter-fn]
  Object
  (roll-sort [this i]
    (let [j (inc i)
          next-icon
          (-> (case (@sort-icons i "fa-sort")
                "fa-sort" ["fa-sort-up" (swap! sort-state conj j)]
                "fa-sort-up" ["fa-sort-down" (swap! sort-state (partial replace {j (- j)}))]
                "fa-sort-down" ["fa-sort" (swap! sort-state (partial remove #(= % (- j))))]) first)]
      (swap! sort-icons assoc i next-icon)
      (sorter-fn @sort-state)))

  (set-sort [this i ord]
    (let[j (inc i)
         next-icon (str "fa-sort" (and ord (str "-" ord)))
         k (c/index-where #(= j (abs %)) @sort-state)
         nj (case ord "up" j "down" (- j))]
      (when-not (= next-icon (@sort-icons i))
        (swap! sort-icons assoc i next-icon)
        (if (some? ord)
          (swap! sort-state #(if k (update % k nj) (conj % nj)))
          (swap! sort-state (partial remove #(= (abs %) j))))
        (sorter-fn @sort-state)))))


(defn column-toolbar [show? i sort-control]
  [:div {:style {:position "absolute" :float "right"
                 :display (if @show? "" "none")}}
   [c/button-group {:bsSize "xsmall"}
    [c/button [:i.fa.fa-filter]]
    [c/button [:i.fa.fa-minus]]
    [c/button [:i.fa.fa-plus]]
    [c/button {:on-click #(.set-sort sort-control i "up")} [:i.fa.fa-sort-up]]
    [c/button {:on-click #(.set-sort sort-control i "down")} [:i.fa.fa-sort-down]]
    [c/button [:i.fa.fa-sort]]]])

(defn scroll-bottom? [e]
  (let [elem (.-target e)
        scroll-top (.-scrollTop elem)
        gap (#(-> (.-scrollHeight %)
                  (- scroll-top)
                  (- (.-clientHeight %))) elem)]
    (and (> scroll-top 0) (< gap 2))))

(defn data-table [data sort-fn refresh-fn next-page-fn]
  (let [sort-state (atom [])
        sort-icons (atom {})
        sort-control (SortControl. sort-state sort-icons sort-fn)
        roll-sort (fn[i]
                    (let [j (inc i)
                          next-icon
                          (-> (case (@sort-icons i "fa-sort")
                                "fa-sort" ["fa-sort-up" (swap! sort-state conj j)]
                                "fa-sort-up" ["fa-sort-down" (swap! sort-state (partial replace {j (- j)}))]
                                "fa-sort-down" ["fa-sort" (swap! sort-state (partial remove #(= % (- j))))]) first)]
                      (sort-fn @sort-state)
                      (swap! sort-icons assoc i next-icon)))]
    (fn [data sort-fn refresh-fn scroll-bottom-fn]
      [:div.table-responsive
       {:style {:overflow-y "scroll" :height "100%"}
        :on-scroll #(when (scroll-bottom? %)
                      (next-page-fn))}
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
                 [column-toolbar show? i sort-control]])) (@data "columns")))]]
        [:tbody
         (map-indexed
          (fn [i row]
            ^{:key i}[:tr [:td (inc i)]
                      (map-indexed
                       (fn[j v] ^{:key j}[:td v]) row)]) (@data "rows"))]
        [:tfoot
         [:tr [:td {:col-span (inc (count (@data "columns")))}
               [c/button {:on-click next-page-fn}
                [:i.fa.fa-chevron-down]]]]]]])))

(defn execute-query [ds query data-fn error-fn]
  (POST (str "/ds/" (ds "id") "/exec-sql")
        :params query :response-format :json :format :json
        :handler data-fn
        :error-handler error-fn))

(defn query-table [ds query]
  (let [data (atom {})
        error (atom nil)
        cur-query (atom query)
        refresh-fn (fn[](execute-query ds (assoc @cur-query :limit (max (count (@data "rows")) 40)) #(reset! data (% "data")) #(reset! error %)))
        sort-data-fn
        (fn[sort-state]
          (let [order-by
                (if-not (empty? sort-state)
                  (->> sort-state
                       (map #(if (neg? %) (str (- %) " desc") %))
                       (s/join ",")
                       (str " order by ")))]
            (reset! cur-query (update query :raw-sql #(-> % (s/replace #"(?im)\s+order\s+by\+.+$" "") (str order-by))))
            (refresh-fn)))
        next-page-fn
        (fn[]
          (execute-query ds (assoc @cur-query :offset (count (@data "rows")))
                         (fn[{{:strs[rows]} "data"}]
                           (swap! data assoc "rows" (apply conj (@data "rows") rows)))
                         #(reset! error %)))]
    (refresh-fn)
    (fn[ds query]
      (if (some? @error)
        [c/alert {:bsStyle "danger"} (or (:response @error) (get-in @error [:parse-error :original-text]))]
        [data-table data sort-data-fn refresh-fn next-page-fn]))))
