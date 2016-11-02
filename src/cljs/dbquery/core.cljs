(ns dbquery.core
  (:require [ajax.core :refer [GET]]
            [dbquery.commons :refer [alert button close-button error-text input
                                     menu-item nav nav-brand nav-dropdown
                                     nav-item navbar open-modal tab tabs]]
            [dbquery.db-admin :as dba]
            [dbquery.db-console :refer [db-console]]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-frame.core :refer [dispatch register-handler subscribe trim-v]]
            [reagent.core :as r :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [cljsjs.mousetrap]
            [dbquery.subs]
            [dbquery.handlers]
            [dbquery.data-import :refer[import-data-tab]])
  (:import goog.History))

(register-handler
 :edit-db
 [trim-v]
 (fn [state [db-id]]
   (if db-id
     (GET (str "/data-sources/" db-id) :response-format :json :keywords? true :format :json
          :handler #(dispatch [:change :modal [dba/database-window %]])
          :error-handler #(dispatch [:change [:edit-db :error] (error-text %)])))
   (if db-id state (assoc state :modal [dba/database-window {}]))))

(register-handler
 :db-saved
 [trim-v]
 (fn [state [db]]
   (dispatch [:open-db db])
   (dissoc state :modal :edit-db)))

(register-handler
 :select-db
 trim-v
 (fn [state []]
   (GET "/data-sources" :response-format :json :keywords? true
        :handler #(dispatch [:change :modal [dba/select-db-dialog %]])
        :error-handler #(js/console.log "failed retrieving dbs..." %))
   state))

(defn toast [message ttl]
  (let [container (js/document.getElementById "alerts")]
    (r/unmount-component-at-node container)
    (r/render [alert {:dismissAfter ttl} message] container)))

(defn alerts-box []
  (let [status (subscribe [:state :status])]
    (fn []
      (when @status
        (.setTimeout js/window #(dispatch [:change :status nil] 5000))
        [:div {:style {:position "absolute" :top "50" :z-index 100 :width 400 :margin "auto"}}
         [alert {:bsStyle (let [style (first @status)] (case style :error "danger" (name style)))
                 :dismissAfter 5} (second @status)]]))))

(defn admin-tab []
  [:div "Admin data"])

(defn home-page []
  (let [db-tabs (subscribe [:db-tabs])
        user (subscribe [:state :user])
        modal (subscribe [:state :modal])
        active-tab (subscribe [:state :active-tab])]
    (js/Mousetrap.bind "alt+o", #(dispatch [:select-db]))
    (fn[]
      [:div {:style {:height "100%"}}
       [alerts-box]
       [navbar {:class "navbar-inverse" :fluid true}
        [nav-brand [:i.fa.fa-database.fa-5x]]
        [nav
         [nav-item {:href "#/"} "Home"]
         [nav-dropdown {:title "Databases" :id "db-dropdown"}
          [menu-item {:on-select #(dispatch [:edit-db nil] open-modal )}
           "Add ..." ]
          [menu-item {:on-select #(dispatch [:select-db])}
           "Open ..."]]
         [nav-item {:on-click #(dispatch [:show-tab :import-data "Import Data"])}
          "Import Data"]
         [nav-item {:on-click #(dispatch [:show-tab :admin "Admin"])}
          "Admin"]]
        [nav {:pull-right true}
         [nav-dropdown {:id "user-dropdown" :title (r/as-element
                                                    [:span [:i.fa.fa-user]
                                                     (get @user "nick")])}
          [menu-item "Profile"]
          [menu-item {:on-click #(dispatch [:logout])}
           "Logout"]]]]
       (if @modal
         [:div {:id "modals"} @modal])
       [:div.container-fluid {:style {:height "calc(100% - 90px)"}}
        (if (seq @db-tabs)
          [tabs {:activeKey @active-tab :on-select #(dispatch [:activate-db %])
                 :class "small-tabs full-height"}
           (doall
            (for [[id a-tab] @db-tabs] ^{:key id}
              [tab {:eventKey id :class "full-height"
                    :title (r/as-element
                            [:span (-> a-tab :name)
                             [close-button #(dispatch [:kill-db id])]])}
               (case id
                 :import-data [import-data-tab]
                 :admin [admin-tab]
                 [db-console id])]))
           ;; (doall (for [id @tab-ids]
           ;;    ^{:key id} [db-tab id]))
           ;; (if (:import-data @special-tabs)
           ;;   [tab {:eventKey :import-data
           ;;         :title (r/as-element
           ;;                 [:span "Import Data"
           ;;                  [close-button #(swap! special-tabs disj :import-data)]])}
           ;;    [import-data-tab]])
           ;; (if (:admin @special-tabs)
           ;;   [tab {:eventKey :admin
           ;;         :title (r/as-element
           ;;                 [:span "Admin"
           ;;                  [close-button #(swap! special-tabs disj :admin)]])}
           ;;    [admin-tab]])
           ]
          [:h3 "Welcome to Data Explorer. Open a DB ..."])]])))

(defn login-page []
  (let [login-data (r/atom {})
        error (subscribe [:state :error])]
    (fn []
      [:div.col-md-offset-4.col-md-3
       [:form {:on-submit #(dispatch [:login @login-data])}
        [:h2 "Sign in"
         [:a.btn-link.btn.btn-lg {:href "#/register"} "or register as new user"]]
        [:div (if @error [alert {:bsStyle "danger"} @error])]
        [input {:model [login-data :userName] :type "text"
                :placeholder "User Name"}]
        [input {:model [login-data :password] :type "password"
                :placeholder "Password"}]
        [:div {:style {:text-align "right"}}
         [button {:bsStyle "primary"
                  :on-click #(dispatch [:login @login-data])} "Sign in"]]]])))

(defn about-page []
  [:div [:h2 "About dbquery"]
   [:div [:a {:href "#/"} "go to the home page"]]])

(defn register-page []
  (let [user (atom {})
        error (subscribe [:state :error])]
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
                  :on-click #(dispatch [:register-user @user])} "Submit"]]]])))

(defn logout-page []
  [:div
   [:a {:href "#/login"} "Login back"]])

(defn current-page []
  (let [cur-page (subscribe [:state :current-page])]
    (fn []
      [:div [(or @cur-page :div)]])))

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (dispatch [:change-page #'home-page true]))

(secretary/defroute "/about" []
  (dispatch [:change-page #'about-page false]))

(secretary/defroute "/login" []
  (dispatch [:change-page #'login-page false]))

(secretary/defroute "/register" []
  (dispatch [:change-page #'register-page false]))

(secretary/defroute "/logout" []
  (dispatch [:change-page #'logout-page false]))
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
  (dispatch [:init-db])

  (doto js/Mousetrap
    (.bind "alt+d" #(dispatch [:preview-table]))
    (.bind "/" #(dispatch [:set-in-active-db :q ""]))
    (.bind "esc" #(dispatch [:set-in-active-db :q nil])))
  (hook-browser-navigation!)
  (mount-root))
