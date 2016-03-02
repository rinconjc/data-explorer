(ns dbquery.core
  (:require [ajax.core :refer [GET POST]]
            [dbquery.commons :as c :refer [button input error-text alert
                                           nav navbar nav-brand menu-item
                                           nav-dropdown nav-item]]
            [dbquery.data-import :refer [import-data-tab]]
            [dbquery.db-admin :as dba]
            [dbquery.db-console :refer [db-console]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [reagent.core :as r :refer [atom]]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true])
  (:import goog.History))

(def user-session (r/atom nil))
(GET "/user" :handler #(reset! user-session %))

(defn open-modal [modal-comp]
  (let [container (js/document.getElementById "modals")]
    (r/unmount-component-at-node container)
    (r/render modal-comp container)))

(defn toast [message ttl]
  (let [container (js/document.getElementById "alerts")]
    (r/unmount-component-at-node container)
    (r/render [alert {:dismissAfter ttl} message] container)))

(defn admin-tab []
  [:div "Admin data"])

(defn home-page []
  (let [db-tabs (atom [])
        active-tab (atom "")
        open-db (fn [db]
                  (when (some? db)
                    (swap! db-tabs conj db)
                    (reset! active-tab (db "id"))))
        select-db (fn[]
                    (open-modal [dba/select-db-dialog
                                 (fn[action db]
                                   (case action :connect (open-db db)
                                         (.setTimeout js/window
                                                      #(open-modal [dba/database-window (db "id") open-db]))))]))
        special-tabs (atom #{})]
    (js/Mousetrap.bind "alt+o", select-db)
    (fn[]
      [:div {:style {:height "100%"}}
       [navbar {:fluid true}
        [nav-brand "DataExplorer"]
        [nav
         [nav-item {:href "#/"} "Home"]
         [nav-dropdown {:title "Databases" :id "db-dropdown"}
          [menu-item {:on-select #(open-modal [dba/database-window nil open-db])}
           "Add ..." ]
          [menu-item {:on-select select-db}
           "Open ..."]]
         [nav-item {:on-click #(do (swap! special-tabs conj :import-data)
                                   (reset! active-tab :import-data))}
          "Import Data"]
         [nav-item {:on-click #(do (swap! special-tabs conj :admin)
                                   (reset! active-tab :admin))}
          "Admin"]]]
       [:div {:id "modals"}]
       [:div.container-fluid {:style {:height "calc(100% - 90px)"}}
        (if (or (seq @db-tabs) (seq @special-tabs))
          [c/tabs {:activeKey @active-tab :on-select #(reset! active-tab %)
                   :class "small-tabs full-height"}
           (doall
            (for [db @db-tabs :let [id (db "id")]] ^{:key id}
              [c/tab {:eventKey id :class "full-height"
                      :title (r/as-element
                              [:span (db "name")
                               [c/close-button
                                (fn[e] (swap! db-tabs (partial remove #(= % db)))
                                  (if (= id @active-tab)
                                    (reset! active-tab (:id (first @db-tabs)))))]])}
               [db-console db (= id @active-tab)]]))
           (if (:import-data @special-tabs)
             [c/tab {:eventKey :import-data
                     :title (r/as-element
                             [:span "Import Data"
                              [c/close-button #(swap! special-tabs disj :import-data)]])}
              [import-data-tab]])
           (if (:admin @special-tabs)
             [c/tab {:eventKey :admin
                     :title (r/as-element
                             [:span "Admin"
                              [c/close-button #(swap! special-tabs disj :admin)]])}
              [admin-tab]])]
          [:h3 "Welcome to Data Explorer. Open a DB ..."])]])))

(defn login-page []
  (let [login-data (r/atom {})
        error (r/atom nil)
        login-ok #(reset! user-session %)
        login-fail #(reset! error (error-text %))
        do-login (fn [e] (reset! error nil)
                   (POST "/login" :format :json
                         :params @login-data
                         :handler login-ok
                         :error-handler login-fail))]
    (fn []
      [:div.col-md-offset-4.col-md-3
       [:form {:on-submit do-login}
        [:h2 "Sign in"
         [:a.btn-link.btn.btn-lg {:href "#/register"} "or register as new user"]]
        [:div (if @error [alert {:bsStyle "danger"} @error])]
        [input {:model [login-data :userName] :type "text"
                :placeholder "User Name"}]
        [input {:model [login-data :password] :type "password"
                :placeholder "Password"}]
        [:div {:style {:text-align "right"}}
         [button {:bsStyle "primary"
                  :on-click do-login} "Sign in"]]]])))

(defn about-page []
  [:div [:h2 "About dbquery"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn register-page []
  (let [user (atom {})
        error (atom nil)
        save-fn (fn[]
                  (POST "/users" :format :json :params @user
                        :handler #(do ;; (toast "User registered!" 5)
                                    (.log js/console "dispatching to login...")
                                      (secretary/dispatch! "/login"))
                        :error-handler #(reset! error (error-text %))))]
    (fn []
      [:div.col-md-offset-3.col-md-4
       [:h2 "Register new user"]
       [:form.form-horizontal
        [:div (if @error [alert {:bsStyle "danger"} @error])]
        [input {:model [user :nick] :type "text"
                :validator #(> (count %) 0)
                :label "User name"
                :label-class-name "col-sm-6"
                :wrapper-class-name "col-sm-6"}]
        [input {:model [user :password] :type "password"
                :label "Password"
                :validator #(condp >= (count %)
                              0 :error 4 :warning :success)
                :label-class-name "col-sm-6"
                :wrapper-class-name "col-sm-6"}]
        [input {:type "password"
                :validator #(= % (:password @user))
                :label "Repeat Password"
                :label-class-name "col-sm-6"
                :wrapper-class-name "col-sm-6"}]
        [:div {:style {:text-align "right"}}
         [button {:bsStyle "primary"
                  :on-click save-fn} "Submit"]]]])))

(defn current-page []
  [:div [(session/get :current-page)]])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :current-page
                (if (some? @user-session)
                  #'home-page #'login-page)))

(secretary/defroute "/about" []
  (session/put! :current-page #'about-page))

(secretary/defroute "/login" []
  (session/put! :current-page #'login-page))

(secretary/defroute "/register" []
  (session/put! :current-page #'register-page))
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
