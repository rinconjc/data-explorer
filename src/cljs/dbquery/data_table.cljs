(ns dbquery.data-table
  (:require [dbquery.commons :as c]
            [clojure.string :as s]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn error-text [e]
  (or (:response e) (get-in e [:parse-error :original-text])))

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
         k (c/index-where #(= j (Math/abs %)) @sort-state)
         nj (case ord "up" j "down" (- j) nil)]
      (when-not (= next-icon (@sort-icons i))
        (swap! sort-icons assoc i next-icon)
        (if (some? ord)
          (swap! sort-state #(if k (update % k (fn[_] nj)) (conj % nj)))
          (swap! sort-state c/remove-nth k))
        (sorter-fn @sort-state)))))

(deftype SqlQuery [cols tables conditions order group]
  Object
  (condition! [_ col condition]
    (if (empty? (:op condition))
      (swap! conditions dissoc col)
      (swap! conditions assoc col condition)))

  (condition [_ col]
    (@conditions col))

  (order! [_ state]
    (reset! order state))

  (sql [_]
    (let [order-by
          (if-not (empty? @order)
            (->> @order
                 (map #(if (neg? %) (str (- %) " desc") %))
                 (s/join ",")
                 (str " order by ")))
          to-str (fn[t prefix?]
                   (cond
                     (string? t) (str (if prefix? ",") t)
                     (map? t) (let[{:keys[join to on]} t]
                                (str join " JOIN " to " on " on))))
          where (if-not (empty? @conditions)
                  (str " WHERE " (s/join " and " (for [[col {:keys [op value]}] @conditions]
                                                   (str col " " op " '" value "'")))))]

      (str "SELECT " (s/join "," @cols) " FROM " (-> @tables first (to-str false))
           " " (->> @tables rest (map #(to-str % true)) (s/join " ") )
           where " " order-by))))

(defn query-from-sql [raw-sql]
  (SqlQuery. (atom ["*"]) (atom [(str "(" raw-sql ")")]) (atom {}) (atom []) (atom [])))

(defn query-from-table [table]
  (SqlQuery. (atom ["*"]) (atom [table]) (atom {}) (atom []) (atom [])))

(deftype DataController [ds query data error]
  Object
  (execute [_ q data-fn error-fn]
    (POST (str "/ds/" (ds "id") "/exec-sql")
          :params q :response-format :json :format :json
          :handler data-fn
          :error-handler error-fn))

  (refresh [this]
    (swap! data assoc :loading true)
    (.execute this {:raw-sql (.sql query) :limit (max (count (@data "rows")) 40)}
              #(reset! data (% "data")) #(reset! error (error-text %))))

  (sort [this sort-state]
    (.order! query sort-state)
    (.refresh this))

  (next-page [this]
    (when-not (:loading @data)
      (swap! data assoc :loading true)
      (.execute this {:raw-sql (.sql query) :offset (count (@data "rows"))}
                (fn[{{:strs[rows]} "data"}]
                  (swap! data assoc "rows" (apply conj (@data "rows") rows)
                         :loading false))
                #(reset! error (error-text %)))))

  (filter [this col condition]
    (.condition! query col condition)
    (.refresh this)))

(defn filter-box [col controller]
  (let[condition (atom (-> controller .-query (.condition col) (or {})))]
    (fn[col controller]
      [:div {:style {:z-index 100}}
       [:form.form-inline
        [c/input (c/bind-value condition :op :type "select" :id "operator")
         [:option {:value ""} "none"]
         [:option {:value "="} "="]
         [:option {:value "!="} "!="]
         [:option {:value "like"} "like"]
         [:option {:value "between"} "between"]
         [:option {:value "<"} "<"]
         [:option {:value "<="} "<="]
         [:option {:value ">"} ">"]
         [:option {:value ">="} ">="]]
        [c/input (c/bind-value condition :value :type "text" :id "value")]
        [c/button {:bs-style "default" :on-click #(.filter controller col @condition)}
         "OK"]]])))

(defn column-toolbar [show? i col sort-control controller]
  (let[filter-on? (atom false)]
    (fn[show? i col sort-control controller]
      (if @show?
        [:div.my-popover
         [c/button-group {:bsSize "xsmall"}
          [c/button {:on-click #(swap! filter-on? not)} [:i.fa.fa-filter]]
          [c/button [:i.fa.fa-minus]]
          [c/button [:i.fa.fa-plus]]
          [c/button {:on-click #(.set-sort sort-control i "up") :ttile "Sort Asc"} [:i.fa.fa-sort-up]]
          [c/button {:on-click #(.set-sort sort-control i "down") :title "Sort Desc"} [:i.fa.fa-sort-down]]
          [c/button {:on-click #(.set-sort sort-control i nil) :title "No Sort"} [:i.fa.fa-sort]]]
         (if @filter-on?
           [filter-box col controller])]))))

(defn scroll-bottom? [e]
  (let [elem (.-target e)
        scroll-top (.-scrollTop elem)
        gap (#(-> (.-scrollHeight %)
                  (- scroll-top)
                  (- (.-clientHeight %))) elem)]
    (and (> scroll-top 0) (< gap 2))))

(defn table-row [data row i]
  (if (map? row)
    [:tr [:td (inc i)]
     (for [c (@data "columns")] ^{:key c}[:td (row c)])]
    [:tr [:td (inc i)]
     (map-indexed
      (fn[j v] ^{:key j}[:td v]) row)]))

(defn data-table [data controller]
  (let [sort-state (atom [])
        sort-icons (atom {})
        sort-control (SortControl. sort-state sort-icons #(.sort controller %))]
    (fn [data controller]
      [:div.full-height {:style {:position "relative"}}
       [:div.table-responsive
        {:style {:overflow-y "scroll" :height "100%" :position "relative"}
         :on-scroll #(when (scroll-bottom? %)
                       (.next-page controller))}
        [:table.table.table-hover.table-bordered.summary
         [:thead
          [:tr [:th {:style {:width "1px" :padding-left "2px" :padding-right "2px"}}
                [c/split-button {:style {:display "flex"}
                                 :title (r/as-element [:i.fa.fa-refresh])
                                 :on-click #(.refresh controller) :bsSize "xsmall"}
                 [c/menu-item {:eventKey 1} "Join with ..."]]]
           (doall
            (map-indexed
             (fn[i c]
               (let [show? (atom false)]
                 ^{:key i}
                 [:th
                  [:a.btn-link {:on-click #(swap! show? not)} c]
                  [:a.btn-link {:on-click #(.roll-sort sort-control i)}
                   [:i.fa.btn-sort {:class (@sort-icons i "fa-sort")}]]
                  [column-toolbar show? i c sort-control controller]])) (@data "columns")))]]
         [:tbody
          (map-indexed
           (fn [i row]
             ^{:key i} [table-row data row i]) (@data "rows"))]
         [:tfoot
          [:tr [:td {:col-span (inc (count (@data "columns")))}
                [c/button {:on-click #(.next-page controller)}
                 [:i.fa.fa-chevron-down]]]]]]]
       (if (:loading @data)
         [c/progress-overlay])])))

(defn execute-query [ds query data-fn error-fn]
  (POST (str "/ds/" (ds "id") "/exec-sql")
        :params query :response-format :json :format :json
        :handler data-fn
        :error-handler error-fn))

(defn query-table [ds query initial]
  (let [data (atom initial)
        error (atom nil)
        controller (DataController. ds query data error)]
    (if-not @data
      (.refresh controller))

    (fn[ds query initial]
      (if (some? @error)
        [c/alert {:bsStyle "danger"} @error]
        [data-table data controller]))))
