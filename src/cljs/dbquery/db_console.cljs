(ns dbquery.db-console
  (:require [dbquery.commons :as c]
            [widgets.splitter :as st]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [GET POST]]))

(defn db-console [db]
  [st/horizontal-splitter 200 [:div {:class "fill-height"} "this is left pane"]
   500 [:div {:class "fill-height"} "this is right pane"]])
