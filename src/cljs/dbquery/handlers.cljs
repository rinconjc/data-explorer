(ns dbquery.handlers
  (:require [ajax.core :refer [GET POST PUT default-interceptors success? to-interceptor]]
            [ajax.protocols :refer [-body -status]]
            [dbquery.commons :as c :refer [error-text open-modal]]
            [reagent.core :as r :refer [atom]]
            [secretary.core :as secretary :include-macros true]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [reagent.ratom :refer-macros [reaction]]))

;; add ajax interceptor
(defn empty-means-nil [response]
  (if-not (str/blank? (-body response))
    response
    (reduced [(-> response -status success?) nil])))

(def treat-nil-as-empty
  (to-interceptor {:name "JSON special case nil"
                   :response empty-means-nil}))

(swap! default-interceptors concat [treat-nil-as-empty])

;; event handlers

(rf/register-handler
 :login
 (fn [state [_ login-data]]
   (POST "/login" :format :json
         :params login-data
         :handler #(rf/dispatch [:login-ok %])
         :error-handler #(rf/dispatch [:server-error %]))
   state))

(rf/register-handler
 :login-ok
 (fn [state [_ user-info]]
   (secretary/dispatch! "/")
   (assoc state :user user-info)))

(rf/register-handler
 :server-error
 (fn [state [_ resp]]
   (assoc state :error (c/error-text resp))))

(rf/register-handler
 :register-user
 (fn [state [_ user-data]]
   (POST "/users" :format :json :params user-data
         :handler #(rf/dispatch [:user-register-ok %])
         :error-handler #(rf/dispatch [:server-error %]))
   state))

(rf/register-handler
 :user-register-ok
 (fn [state [_ _]]
   (secretary/dispatch! "/login")
   state))

(rf/register-handler
 :logout
 (fn [state _]
   (secretary/dispatch! "/logout")
   (dissoc state :user)))

(rf/register-handler
 :change
 (fn [state [_ key value]]
   (if (vector? key)
     (assoc-in state key value)
     (assoc state key value))))

(rf/register-handler
 :change-page
 (fn [state [_ page auth-required?]]
   (if (or (not auth-required?) (:user state))
     (assoc state :current-page page)
     (do
       (GET "/user" :handler #(rf/dispatch [:login-ok %])
            :error-handler #(secretary/dispatch! "/login"))
       state))))

(rf/register-handler
 :save-query
 (fn [state [_ tab-id query success-fn]]
   (js/console.log "handling save query:" query)
   (let [query (if (string? query)
                 (assoc (get-in state [:tabs tab-id :sql-panel :query]) :sql query)
                 query)
         [method path] (if-let [id (:id query)] [PUT (str "/queries/" id)] [POST "/queries"])]
     (method path :params query :format :json
             :handler #(do (rf/dispatch [:change [:tabs tab-id :sql-panel :query %]])
                           (and (fn? success-fn) (success-fn)))
             :error-handler #(rf/dispatch [:change :status [:error (c/error-text %)]]))
     state)))

;; register queries
(rf/register-sub
 :state
 (fn [state [_ key]]
   (if (vector? key)
     (reaction (get-in @state key))
     (reaction (get @state key)))))
