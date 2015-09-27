(ns dbquery.core
    (:require [reagent.core :as r :refer [atom]]
              [reagent.session :as session]
              [secretary.core :as secretary :include-macros true]
              [goog.events :as events]
              [goog.history.EventType :as EventType]
              [cljsjs.react-bootstrap])
    (:import goog.History))

;; --------------------------
;; navigation menu
(def navbar (r/adapt-react-class js/ReactBootstrap.Navbar))
(def nav (r/adapt-react-class js/ReactBootstrap.Nav))
(def nav-item (r/adapt-react-class js/ReactBootstrap.NavItem))
(def nav-dropdown (r/adapt-react-class js/ReactBootstrap.NavDropdown))
(def menu-item (r/adapt-react-class js/ReactBootstrap.MenuItem))
(def modal (r/adapt-react-class js/ReactBootstrap.Modal))
(def modal-header (r/adapt-react-class js/ReactBootstrap.Modal.Header))
(def modal-body (r/adapt-react-class js/ReactBootstrap.Modal.Body))
(def modal-footer (r/adapt-react-class js/ReactBootstrap.Modal.Footer))
(def button (r/adapt-react-class js/ReactBootstrap.Modal.Button))


(defn text-field [name label :keys {placeholder label-class field-class}]
  [:div.form-group
   [:label.control-label {:for name :class field-class} label]
   [:div {:class label-class}
    [:input.form-control {:type "text" :id name :placeholder placeholder}]]])
;; -------------------------
;; Views

(defn database-window []
  [modal
   [modal-header "Database Connection"]
   [modal-body
    [:form.horizontal
     [text-field "name" "Name"
      {:placelhoder "A unique name" :label-class "col-sm-4" :field-class "col-sm-8"}]
     [text-field "other" "other"
      {:placelhoder "A unique name" :label-class "col-sm-4" :field-class "col-sm-8"}]]]
   [modal-footer
    ]])

(defn home-page []
  [:div
   [navbar {:brand "DataExplorer"}
    [nav
     [nav-item {:href "#/"} "Home"]
     [nav-dropdown {:title "Databases" :id "db-dropdown"}
      [menu-item "Add ..."]
      [menu-item "Open ..."]]
     ]]])

(defn about-page []
  [:div [:h2 "About dbquery"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (secretary/dispatch! (.-token event))))
    (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn mount-root []
  (r/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
