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
(def panel (r/adapt-react-class js/ReactBootstrap.Panel))
(def list-group (r/adapt-react-class js/ReactBootstrap.ListGroup))
(def list-group-item (r/adapt-react-class js/ReactBootstrap.ListGroupItem))





(defn bind-value [an-atom id & attrs]
  (apply hash-map  (list* :value (@an-atom id)
                          :on-change (fn [e]
                                       (swap! an-atom #(assoc % id (-> e .-target .-value)))) attrs)))

(defn remove-x [xs x]
  (remove #(= x %) xs))

(defn close-button [close-fn]
  [button {:on-click close-fn :class "close"}
   [:span "×"]])
