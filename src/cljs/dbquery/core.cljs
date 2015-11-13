(ns dbquery.core
  (:require [reagent.core :as r :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [dbquery.db-admin :as dba]
            [dbquery.commons :as c]
            [dbquery.db-console :refer [db-console]]
            [ajax.core :refer [GET POST]]
            [cljsjs.mousetrap])
  (:import goog.History))

(def user-session (r/atom nil))
(GET "/user" :handler #(reset! user-session %))

(defn open-modal [modal-comp]
  (let [container (js/document.getElementById "modals")]
    (r/unmount-component-at-node container)
    (r/render modal-comp container)))

;; --------------------------
;; navigation menu
;; -------------------------
;; Views

(defn edit-db-details [db-id done-fn]
  (let [db-info (atom {})]
    (if db-id
      (GET (str "/data-sources/" db-id) :response-format :json :keywords? true :format :json
           :handler #(reset! db-info %) :error-handler #(.log js/console (c/error-text %))))
    (open-modal [dba/database-window db-info done-fn])))

(defn home-page []
  (let [db-tabs (atom [])
        active-tab (atom "")
        open-db (fn [db] (when (some? db)
                           (swap! db-tabs #(conj % db))
                           (reset! active-tab (db "id"))))
        select-db (fn[](open-modal [dba/select-db-dialog
                                    (fn[action db](case action :connect (open-db db)
                                           (.setTimeout js/window
                                                        #(edit-db-details (db "id") open-db))))]))]
    (js/Mousetrap.bind "alt+o", select-db)
    (fn[]
      [:div {:style {:height "100%"}}
       [c/navbar {:brand "DataExplorer" :fluid true}
        [c/nav
         [c/nav-item {:href "#/"} "Home"]
         [c/nav-dropdown {:title "Databases" :id "db-dropdown"}
          [c/menu-item {:on-select #(edit-db-details nil open-db)}
           "Add ..." ]
          [c/menu-item {:on-select select-db}
           "Open ..."]]
         [c/nav-item {:href "#/"} "Import Data"]]]
       [:div {:id "modals"}]
       [:div.container-fluid {:style {:height "calc(100% - 90px)"}}
        (if-not (empty? @db-tabs)
          [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %)
                  :class "small-tabs full-height"}
          (doall
           (for [db @db-tabs :let [id (db "id")]]
             ^{:key id} [c/tab {:eventKey id :class "full-height"
                                :title (r/as-element [:span (db "name") [c/close-button (fn[e] (swap! db-tabs c/remove-x db))]])}
                         [db-console db (= id @active-tab)]]))]
          [:h3 "Welcome to Data Explorer. Open a DB ..."])]])))

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
  (r/render [current-page] (.-body js/document)))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
