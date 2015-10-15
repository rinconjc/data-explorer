(ns widgets.splitter
  (:require [reagent.core :as r :refer [atom]]))
;; [splitter :vertical panes]
;;

(defn- splitter-attrs [orientation pos]
  (let [css-pos (if (string? pos) pos (str pos "px"))]
    (if (= orientation :vertical)
     {:handler {:top css-pos}
      :pane1 {:height css-pos}
      :pane2 {:top css-pos}
      :css-class "split-panes vertical"}
     {:handler {:left css-pos}
      :pane1 {:width css-pos}
      :pane2 {:left css-pos}
      :css-class "split-panes horizontal"})))

(defn splitter [{:keys [orientation min-size split-at] :or {split-at "50%" min-size [0 0]}} pane1 pane2]
  (let [elem (atom nil)
        split-pos (atom split-at)
        styles (atom (splitter-attrs orientation split-at))
        update-pos (fn [full-size pos]
                     (when-not (or (< pos (min-size 0)) (< (- full-size pos) (min-size 1)))
                       (reset! styles (splitter-attrs orientation pos))))
        mouse-move (fn[e]
                     (when-let [bounds (and @elem (-> @elem .getBoundingClientRect))]
                       (if (= :vertical orientation)
                         (update-pos (- (.-bottom bounds) (.-top bounds))
                                     (- (.-clientY e) (.-top bounds)))
                         (update-pos (- (.-right bounds) (.-left bounds))
                                     (- (.-clientX e) (.-left bounds)))))
                     (.preventDefault e))]

    (.addEventListener js/document "mouseup" #(reset! elem nil))

    (fn[]
      [:div {:class (:css-class @styles) :on-mouse-move mouse-move}
       [:div {:class "split-pane1" :style (:pane1 @styles)} pane1]
       [:div {:class "split-handler" :style (:handler @styles)
              :on-mouse-down (fn[e] (reset! elem (-> e .-target .-parentElement))
                               (.preventDefault e))}]
       [:div {:class "split-pane2" :style (:pane2 @styles)} pane2]])))

(defn vertical-splitter [opts pane1 pane2]
  [splitter (assoc opts :orientation :vertical) pane1 pane2])

(defn horizontal-splitter [opts pane1 pane2]
  [splitter (assoc opts :orientation :horizontal) pane1 pane2])
