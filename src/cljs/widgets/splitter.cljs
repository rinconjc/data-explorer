(ns widgets.splitter
  (:require [reagent.core :as r :refer [atom]]))
;; [splitter :vertical panes]
;;

(defn- pane1-style [orientation pos]
  (let [css-pos (if (string? pos) pos (str pos "px"))]
    (if (= orientation :vertical)
      {:height css-pos}
      {:width css-pos})))


(defn splitter [{:keys [orientation min-size split-at] :or {split-at "50%" min-size [0 0]}} pane1 pane2]
  (let [elem (atom nil)
        collapsed (atom false)
        styles (atom (pane1-style orientation split-at))
        update-pos (fn [full-size pos]
                     (when-not (or (< pos (min-size 0)) (< (- full-size pos) (min-size 1)))
                       (reset! styles (pane1-style orientation pos))))
        mouse-move (fn[e]
                     (when-let [bounds (and @elem (-> @elem .getBoundingClientRect))]
                       (if (= :vertical orientation)
                         (update-pos (- (.-bottom bounds) (.-top bounds))
                                     (- (.-clientY e) (.-top bounds)))
                         (update-pos (- (.-right bounds) (.-left bounds))
                                     (- (.-clientX e) (.-left bounds)))))
                     (.preventDefault e))]

    (.addEventListener js/document "mouseup" #(reset! elem nil))

    (fn[{:keys [orientation min-size split-at] :or {split-at "50%" min-size [0 0]}} pane1 pane2]
      [:div.split-panes {:class (conj [(name orientation)] (when @collapsed "collapsed"))
                         :on-mouse-move mouse-move}
       [:div.split-pane1 {:style @styles} pane1]
       [:div.split-handler (when-not @collapsed
                             {:on-mouse-down (fn[e] (reset! elem (-> e .-target .-parentElement))
                                               (.preventDefault e))})
        (if (= orientation :vertical)
          [:a.btn-link.btn-lg {:on-click #(swap! collapsed not)}
           [:i.fa {:class (if @collapsed "fa-angle-double-down" "fa-angle-double-up")}]]
          [:a.btn-link.btn-lg {:on-click #(swap! collapsed not)}
           [:i.fa {:class (if @collapsed "fa-angle-double-right" "fa-angle-double-left")}]])]
       [:div {:class "split-pane2" :style (:pane2 @styles)} pane2]])))

(defn vertical-splitter [opts pane1 pane2]
  [splitter (assoc opts :orientation :vertical) pane1 pane2])

(defn horizontal-splitter [opts pane1 pane2]
  [splitter (assoc opts :orientation :horizontal) pane1 pane2])
