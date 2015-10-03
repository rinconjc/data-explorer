(ns dbquery.core
  (:require [reagent.core :as r :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [dbquery.db-admin :as dba]
            [dbquery.commons :as c])
  (:import goog.History))

;; --------------------------
;; navigation menu

;; -------------------------
;; Views

(defn show-modal [e]
  (let [show? (r/atom true)
        data (r/atom {:name "db name" :dbms "H2"})
        comp (r/render [dba/database-window show? data] (.-target e))]
    (doseq [c (r/children comp)]
      (.log js/console "child:" c))))

(defn home-page []
  [:div
   [c/navbar {:brand "DataExplorer"}
    [c/nav
     [c/nav-item {:href "#/"} "Home"]
     [c/nav-dropdown {:title "Databases" :id "db-dropdown"}
      [c/menu-item {:on-select show-modal} "Add ..." ]
      [c/menu-item "Open ..."]]
     ]]])

(defn login-page []
  (let [login-data (r/atom {})
        error (r/atom nil)]
    (fn []
     [:div
      [:form
       [:h2 "Please sign in"]
       [:div (if @error [c/alert {:bsStyle "Danger"} @error])]
       [c/input (c/bind-value login-data :userName :type "text"
                              :placeholder "User Name")]
       [c/input (c/bind-value login-data :password :type "password"
                              :placeholder "User Name")]
       [c/button {:bsStyle "primary"
                  :on-click (fn [e] (reset! error nil)
                              )}]]])))

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
