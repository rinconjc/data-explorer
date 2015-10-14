(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))


(defn retrieve-db-objects [db resp-atom error-atom & {:keys [refresh]}]
  (GET (str "/ds/" (db "id") "/tables?refresh=" refresh) :response-format :json
       :handler #(reset! resp-atom %)
       :error-handler #(reset! error-atom %)))

(defn db-objects [db]
  (let [tables (atom [])
        error (atom nil)
        selected (atom nil)
        pointed (atom nil)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}]
    (retrieve-db-objects [db tables error])

    (fn []
      [:div {:class "full-height panel panel-default"}
       [:div.panel-heading {:class "compact"}
        [c/button-group
         [c/button [:span.glyphicon
                    {:class "glyphicon-refresh" :title "Refresh Objects"
                     :on-click #(retrieve-db-objects [db tables error] :refresh true)}]]
         [c/button [:span.glyphicon
                    {:class "glyphicon-list-alt" :title "Preview Data"
                     :on-click #()}]]
         [c/button [:span.glyphicon {:class "glyphicon-info-sign" :title "Show metadata"}]]]]
       [:div.panel-body {:on-mouse-leave #(reset! pointed nil)}
        [:span @error]
        [:ul {:class "list-unstyled" :style {:height "100%" :cursor "pointer"}}
         (doall (for [tb @tables]
                  ^{:key (tb "name")}
                  [:li {:class (condp = tb @selected "selected" @pointed "pointed" "")
                        :on-click #(reset! selected tb)
                        :on-mouse-over #(reset! pointed tb)}
                   [:i.fa {:class (icons (tb "type"))}] (tb "name")]))]]])))

(defn db-console [db]
  (let [active-tab (atom nil)
        data-tabs (atom [])
        ops {:preview-table (fn[tbl]
                              (if (some #(= tbl (:id %)) @data-tabs)
                                (reset! active-tab tbl)
                                (swap! data-tabs )))}]
    (fn[]
      [st/horizontal-splitter {:split-at 240}
       [db-objects db]
       [st/vertical-splitter {:split-at 200}
        [:div "top pane"]
        [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %) :class "small-tabs full-height" :style {:display (if (empty? @data-tabs) "none" "")}}
         (for [t @data-tabs]^{:key (:id t)}
              [c/tab {:eventKey (:id t)
                      :title (r/as-element [:span (:id t)
                                            [c/close-button #(swap! data-tabs (partial remove #(= t %)))]])}
               [query-table db (:query t)]])]]])))
