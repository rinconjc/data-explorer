(ns widgets.splitter
  (:require [reagent.core :as r :refer [atom]]))
;; [splitter :vertical panes]
;;

(defn splitter [{:keys [orientation min-size]} pane1 pane2]
  (let [drag (atom false)
        split-pos (atom 0)
        update-pos (fn [full-size pos]
                     (if-not (or (< pos (min-size 0)) (< (- full-size pos) (min-size 1)))
                       (reset! split-pos pos)))
        mouse-move (fn[e]
                     (when-let [bounds (and @drag (-> e .-target .getBoundingClientRect))]
                       (if (= :vertical orientation)
                         (update-pos (- (.-bottom bounds) (.-top bounds))
                                     (- (.-clientY e) (.-top bounds)))
                         (update-pos (- (.-right bounds) (.-left bounds))
                                     (- (.-clientX e) (.-left bounds))))))
        styles (if (= orientation :vertical)
                 {:handler {:top (str @split-pos "px")}
                  :pane1 {:height (str @split-pos "px")}
                  :pane2 {:top (str @split-pos "px")}
                  :css-class "split-panes vertical"}
                 {:handler {:left (str @split-pos "px")}
                  :pane1 {:width (str @split-pos "px")}
                  :pane2 {:left (str @split-pos "px")}
                  :css-class "split-panes horizontal"})]

    (.addEventListener js/document "mouseup" #(reset! drag false))
    (fn[]
      [:div {:class (:css-class styles) :on-mousemove mouse-move}
       [:div {:class "split-pane1" :style (:pane1 styles)} pane1]
       [:div {:class "split-handler" :style (:handler styles)  :on-mousedown
              (fn[e] (.preventDefault e)
                (reset! drag true))}]
       [:div {:class "split-pane2" :style (:pane2 styles)} pane2]])))

(defn vertical-splitter [min-size1 pane1 min-size2 pane2]
  (splitter {:orientation :vertical :min-size [min-size1 min-size2]} pane1 pane2))

(defn horizontal-splitter [min-size1 pane1 min-size2 pane2]
  (splitter {:orientation :horizontal :min-size [min-size1 min-size2]} pane1 pane2))
