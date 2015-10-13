(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))


(defn db-objects [db]
  (let [tables (atom [])
        error (atom nil)
        selected (atom nil)
        pointed (atom nil)
        icons {"TABLE" "fa-table fa-fw"
               "VIEW" "fa-copy fa-fw"}]
    (GET (str "/ds/" (db "id") "/tables") :response-format :json
         :handler #(reset! tables %)
         :error-handler #(reset! error %))
    (fn []
      [:div {:class "full-height panel panel-default"}
       [:div.panel-heading {:class "compact"}
        [c/button-group
         [c/button [:span.glyphicon {:class "glyphicon-refresh"}]]
         [c/button [:span.glyphicon {:class "glyphicon-list-alt"}]]
         [c/button [:span.glyphicon {:class "glyphicon-info-sign"}]]]]
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
  [st/horizontal-splitter {:split-at 240}
   [db-objects db]
   [st/vertical-splitter {:split-at 200}
    [:div "top pane"]
    [:div "bottom pane"]]])
