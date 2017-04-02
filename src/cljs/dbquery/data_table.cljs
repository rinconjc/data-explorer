(ns dbquery.data-table
  (:require [dbquery.commons :as c :refer [button input]]
            [dbquery.sql-utils :refer [sort-icons]]
            [re-frame.core :refer [dispatch subscribe]]
            [reagent.core :as r :refer [atom]]))

(defn toggle [x y]
  (if-not (= x y) y))

(defn filter-box [query col]
  (let [condition (atom {:value (-> query :conditions (get col))})]
    (fn [query col]
      [:form.form-inline {:style {:padding "4px"}
                          :on-submit #(do (dispatch [:set-filter col (:value @condition)])
                                          (.preventDefault %))}
       [input {:model [condition :value] :type "text" :id "value" :focus true}]
       [button {:bs-style "default" :type "submit"}
        "OK"]])))

(defn dist-values [model col]
  [:div {:style {:padding "5px"}}
   [:ul.list-unstyled.list {:cursor "pointer"}
    (map-indexed
     (fn[i v] ^{:key i}
       [:li {:on-click #(dispatch [:set-filter col (str "='" v "'")])}
        v]) (-> model :col-data (get col)))]])

(defn column-toolbar [model i col]
  (let [active-box (atom nil)]
    (fn [model i col]
      [:div.my-popover
       [c/button-group {:bsSize "xsmall"}
        [c/button {:on-click (fn[](swap! active-box #(case % :filter nil :filter)))}
         [:i.fa.fa-filter]]
        [c/button {:on-click (fn[]
                               (dispatch [:query-dist-values col])
                               (swap! active-box #(case % :distinct nil :distinct)))
                   :title "Distinct Values"}
         [:i.fa.fa-list-alt]]
        [c/button {:on-click #(dispatch [:set-sort i :up]) :title "Sort Asc"} [:i.fa.fa-sort-up]]
        [c/button {:on-click #(dispatch [:set-sort i :down]) :title "Sort Desc"} [:i.fa.fa-sort-down]]
        [c/button {:on-click #(dispatch [:set-sort i nil]) :title "No Sort"} [:i.fa.fa-sort]]]
       (case @active-box
         :filter [filter-box (:query model) col]
         :distinct [dist-values model col]
         "")])))

(defn scroll-bottom? [e]
  (let [elem (.-target e)
        scroll-top (.-scrollTop elem)
        gap (#(-> (.-scrollHeight %)
                  (- scroll-top)
                  (- (.-clientHeight %))) elem)]
    (and (> scroll-top 0) (< gap 2))))

(defmulti table-cell (fn [metadata v] (:type metadata)))

(defmethod table-cell :default [_ v] v)

(defmethod table-cell :image [_ v]
  [:img {:src v}])

(defn table-row [data row i metadata]
  (if (map? row)
    [:tr [:td (inc i)]
     (for [c (data :columns)] ^{:key c}[:td (str (row c))])]
    [:tr [:td (inc i)]
     (doall (map-indexed
             (fn[j v] ^{:key j}
               [:td {:title v}
                (table-cell (nth metadata j) v)]) row))]))

(defn data-table [model col-meta]
  (let [col-toolbar-on (atom nil)]
    (fn [model col-meta]
      [:div.full-height {:style {:position "relative"}}
       [:div.table-responsive
        {:style {:overflow-y "scroll" :height "100%" :position "relative"}
         :on-scroll #(when (scroll-bottom? %)
                       (dispatch [:next-page]))}
        [:table.table.table-hover.table-bordered.summary
         [:thead
          [:tr [:th {:style {:width "1px" :padding-left "2px" :padding-right "2px"}}
                [c/button-group {:bsSize "xsmall" :style {:display "flex"}}
                 [c/button {:title "refresh" :on-click #(dispatch [:reload])} [:i.fa.fa-refresh]]
                 [c/button {:title "more options"
                            :on-click #(swap! col-toolbar-on toggle -1)} "..."]]
                (if (= @col-toolbar-on -1)
                  [:div.my-popover
                   [c/button-group {:bsSize "xsmall" :style {:display "flex"}}
                    [c/button {:title "Filters:"} "filters"]]])]
           (doall
            (map-indexed
             (fn[i c]
               ^{:key i}
               [:th {:on-mouse-leave #(reset! col-toolbar-on nil)}
                [:a.btn-link {:on-mouse-over #(swap! col-toolbar-on toggle i)} c]
                [:a.btn-link {:on-click #(dispatch [:roll-sort i])}
                 [:i.fa.btn-sort {:class (sort-icons (some #(if (= i (first %)) (second %))
                                                           (-> model :query :order)))}]]
                (if-let [condition (-> model :query :conditions (get c))]
                  [:a.btn-link {:on-click #(dispatch [:set-filter c nil])
                                :title (str condition)}
                   [:i.fa.fa-filter]])
                (if (= @col-toolbar-on i)
                  [column-toolbar model i c])]) (-> model :data :columns)))]]
         [:tbody
          (doall (map-indexed
                  (fn [i row] ^{:key i}
                    [table-row (:data model) row i col-meta])
                  (-> model :data :rows)))]
         [:tfoot
          [:tr [:td {:col-span (inc (count (-> model :data :columns)))}
                (if-not (:last-page? model)
                  [c/button {:on-click #(dispatch [:next-page])}
                   [:i.fa.fa-chevron-down]])]]]]]
       (if (:loading model)
         [c/progress-overlay])])))
