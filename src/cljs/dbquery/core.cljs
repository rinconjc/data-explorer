(ns dbquery.core
  (:require [reagent.core :as r :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [dbquery.db-admin :as dba]
            [dbquery.commons :as c]
            [ajax.core :refer [GET POST]]
            [cljsjs.mousetrap])
  (:import goog.History))

(def user-session (r/atom nil))
(GET "/user" :handler #(reset! user-session %))

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

(defn open-db [e]
  (let [dialog (r/render [dba/select-db-dialog #(js/console.log "db selected:" %)]
                         (js/document.getElementById "content"))]
    (js/console.log "mounted dialog:" dialog)
    (r/force-update dialog true)))

(defn home-page []
  (js/Mousetrap.bind "alt+o", open-db)
  [:div
   [c/navbar {:brand "DataExplorer" :fluid true}
    [c/nav
     [c/nav-item {:href "#/"} "Home"]
     [c/nav-dropdown {:title "Databases" :id "db-dropdown"}
      [c/menu-item {:on-select show-modal} "Add ..." ]
      [c/menu-item {:on-select open-db} "Open ..."]]
     ]]
   [:div {:id "content"}]])

(defn login-page []
  (let [login-data (r/atom {})
        error (r/atom nil)
        login-ok (fn [r]
                   (js/console.log "ok response" r)
                   (reset! user-session r))
        login-fail (fn [e]
                     (.log js/console "failed login" e)
                     (reset! error (:status-text e)))
        do-login (fn [e] (reset! error nil)
                   (POST "/login" :format :json
                         :params @login-data
                         :handler login-ok
                         :error-handler login-fail))]
    (fn []
     [:div {:class "form-signin"}
      [:form {:on-submit do-login}
       [:h2 "Please sign in"]
       [:div (if @error [c/alert {:bsStyle "Danger"} @error])]
       [c/input (c/bind-value login-data :userName :type "text"
                              :placeholder "User Name")]
       [c/input (c/bind-value login-data :password :type "password"
                              :placeholder "Password")]
       [c/button {:bsStyle "primary"
                  :on-click do-login} "Sign in"]]])))

(defn about-page []
  [:div [:h2 "About dbquery"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn current-page []
  (if (some? @user-session)
    [:div [(session/get :current-page)]]
    [:div [login-page]]))

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page #'home-page))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/login" []
  (session/put! :current-page #'login-page))
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
  (js/console.log "init...")
  (hook-browser-navigation!)
  (mount-root))
