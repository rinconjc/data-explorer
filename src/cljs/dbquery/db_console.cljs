(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [dbquery.data-table :refer [query-table]]
            [reagent.core :as r :refer [atom]]
            [cljsjs.codemirror]
            [ajax.core :refer [GET POST]]))


(defn retrieve-db-objects [db resp-atom error-atom & {:keys [refresh]}]
  (GET (str "/ds/" (db "id") "/tables?refresh=" refresh) :response-format :json
       :handler #(reset! resp-atom %)
       :error-handler #(reset! error-atom %)))

(defn db-objects [db ops]
  (let [tables (atom [])
        error (atom nil)
        selected (atom nil)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}
        _ (retrieve-db-objects db tables error)]
    (fn [db ops]
      [:div {:class "full-height panel panel-default"}
       [:div.panel-heading {:class "compact"}
        [c/button-group
         [c/button [:span.glyphicon
                    {:class "glyphicon-refresh" :title "Refresh Objects"
                     :on-click #(retrieve-db-objects db tables error :refresh true)}]]
         [c/button {:on-click #((:preview-table ops) (@selected "name"))}
          [:span.glyphicon
           {:class "glyphicon-list-alt" :title "Preview Data"}]]
         [c/button [:span.glyphicon {:class "glyphicon-info-sign" :title "Show metadata"}]]]]
       [:div.panel-body
        [:span @error]
        [:ul {:class "list-unstyled list" :style {:height "100%" :cursor "pointer"}}
         (doall (for [tb @tables]
                  ^{:key (tb "name")}
                  [:li {:class (if (= tb @selected) "selected" "")
                        :on-click #(reset! selected tb)}
                   [:i.fa {:class (icons (tb "type"))}] (tb "name")]))]]])))

(defn db-console [db]
  (let [active-tab (atom nil)
        data-tabs (atom [])
        ops {:preview-table (fn[tbl]
                              (when-not (some #(= tbl (:id %)) @data-tabs)
                                (.log js/console "adding table " tbl)
                                (swap! data-tabs conj {:id tbl :raw-sql (str "select * from " tbl)}))
                              (reset! active-tab tbl))}]
    (fn[db]
      [st/horizontal-splitter {:split-at 240}
       [db-objects db ops]
       [st/vertical-splitter {:split-at 200}
        [:div "top pane, tabs count:" (count @data-tabs) ]
        [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %)
                 :class "small-tabs full-height"}
         (for [t @data-tabs]
           ^{:key (:id t)}
           [c/tab {:eventKey (:id t)
                   :title (r/as-element [:span (:id t)
                                         [c/close-button (fn[_](swap! data-tabs (partial remove #(= t %))))]])}
            [query-table db t]])]]])))
