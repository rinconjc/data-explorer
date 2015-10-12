(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))


(defn db-objects [db]
  (let [tables (atom [])
        error (atom nil)]
    (GET (str "/ds/" (db "id") "/tables") :response-format :json
         :handler #(reset! tables %)
         :error-handler #(reset! error %))
    (fn []
     [:div {:class "full-height panel panel-default"}
      [c/panel {:class "full-height"
                :header (r/as-element [c/button-group
                                       [c/button [:span.glyphicon {:class "glyphicon-refresh"}]]
                                       [c/button [:span.glyphicon {:class "glyphicon-refresh"}]]
                                       [c/button [:span.glyphicon {:class "glyphicon-refresh"}]]])}
       [:div
        [:span @error]
        [:ul {:style {:height "100%"}}
         (for [tb @tables]
           [:li (tb "name")])]]]])))

(defn db-console [db]
  [st/horizontal-splitter {:split-at 300}
   [db-objects db]
   [st/vertical-splitter {:split-at 200}
    [:div "top pane"]
    [:div "bottom pane"]]])
