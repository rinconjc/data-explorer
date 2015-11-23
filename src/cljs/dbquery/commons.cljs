(ns dbquery.commons
  (:require [reagent.core :as r :refer [atom]]
            [cljsjs.react-bootstrap]))

(def navbar (r/adapt-react-class js/ReactBootstrap.Navbar))
(def nav (r/adapt-react-class js/ReactBootstrap.Nav))
(def nav-item (r/adapt-react-class js/ReactBootstrap.NavItem))
(def nav-dropdown (r/adapt-react-class js/ReactBootstrap.NavDropdown))
(def menu-item (r/adapt-react-class js/ReactBootstrap.MenuItem))
(def modal (r/adapt-react-class js/ReactBootstrap.Modal))
(def modal-header (r/adapt-react-class js/ReactBootstrap.Modal.Header))
(def modal-body (r/adapt-react-class js/ReactBootstrap.Modal.Body))
(def modal-footer (r/adapt-react-class js/ReactBootstrap.Modal.Footer))
(def button (r/adapt-react-class js/ReactBootstrap.Button))
(def input (r/adapt-react-class js/ReactBootstrap.Input))
(def alert (r/adapt-react-class js/ReactBootstrap.Alert))
(def tabs (r/adapt-react-class js/ReactBootstrap.Tabs))
(def tab (r/adapt-react-class js/ReactBootstrap.Tab))
(def button-toolbar (r/adapt-react-class js/ReactBootstrap.ButtonToolbar))
(def button-group (r/adapt-react-class js/ReactBootstrap.ButtonGroup))
(def split-button (r/adapt-react-class js/ReactBootstrap.SplitButton))

(def panel (r/adapt-react-class js/ReactBootstrap.Panel))
(def list-group (r/adapt-react-class js/ReactBootstrap.ListGroup))
(def list-group-item (r/adapt-react-class js/ReactBootstrap.ListGroupItem))
(def popover (r/adapt-react-class js/ReactBootstrap.Popover))
(def overlay-trigger (r/adapt-react-class js/ReactBootstrap.OverlayTrigger))

(defn bind-value [an-atom id & attrs]
  (apply hash-map (list* :value (@an-atom id)
                          :on-change (fn [e]
                                       (swap! an-atom assoc id (-> e .-target .-value))) attrs)))
(defn remove-x [xs x]
  (remove #(= x %) xs))

(defn close-button [close-fn]
  [button {:on-click #(do (close-fn)
                          (doto % .stopPropagation .preventDefault))
           :class "close"}
   [:span "×"]])

(defn index-where [pred xs]
  (cond
    (empty? xs) nil
    (pred (first xs)) 0
    :else (if-let [c (index-where pred (rest xs))](inc c))))

(defn remove-nth [v i]
  (vec (concat (subvec v 0 i) (subvec v (inc i)))))

(defn progress-overlay []
  [:div {:style {:position "absolute" :width "100%" :height "100%" :z-index 100
                 :top 0 :left 0 :background "rgba(255,255,255,0.5)"
                 :text-align "center"}}
   [:i.fa.fa-spinner.fa-spin.fa-3x {:style {:margin-top "10%"}}]])

(defn error-text [e]
  (or (:response e) (get-in e [:parse-error :original-text])))

(defn form-group [{:keys[group-class label]} input]
  [:div.form-group {:class group-class}
   [:label label] input])
