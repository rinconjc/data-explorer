(ns dbquery.data-viz
  (:require [dbquery.commons :as c]
            [re-frame.core :refer [dispatch]]
            [thi.ng.geom.svg.core :as svg]
            [thi.ng.geom.viz.core :as viz]
            [thi.ng.math.core :as m]))

(defn bar-spec
  [num width]
  (fn [idx col]
    {:values     (map (fn [i] [i (m/random 100)]) (range 2000 2016))
     :attribs    {:stroke       col
                  :stroke-width (str (dec width) "px")}
     :layout     viz/svg-bar-plot
     :interleave num
     :bar-width  width
     :offset     idx}))

(def viz-spec
  {:x-axis (viz/linear-axis
            {:domain [1999 2016]
             :range  [50 580]
             :major  1
             :pos    280
             :label  (viz/default-svg-label int)})
   :y-axis (viz/linear-axis
            {:domain      [0 100]
             :range       [280 20]
             :major       10
             :minor       5
             :pos         50
             :label-dist  15
             :label-style {:text-anchor "end"}})
   :grid   {:minor-y true}})

(defn chart-of [data {:keys [type] :or {type :bar}}]
  (-> viz-spec
      (assoc :data (map-indexed (bar-spec 3 6) ["#0af" "#fa0" "#f0a"]))
      (viz/svg-plot2d-cartesian)
      ((partial svg/svg {:with 600 :height 320}))))

(defn chart-modal []
  [c/modal {:on-hide #(dispatch [:change :modal nil]) :show true :bsSize "large"}
   [c/modal-header [:h4 "Data Chart"]]
   [c/modal-body
    [chart-of [] {}]]])
