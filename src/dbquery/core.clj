(ns dbquery.core
  (:import [java.util Date])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            ;; [compojure.handler :refer [site]]
            [ring.middleware.json :refer :all]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.reload :as reload]
            [org.httpkit.server :refer [run-server]]
            [dbquery.databases :refer :all]
            [clojure.java.io :as io]
            [korma.core :as k]
            [dbquery.model :refer :all]
            [clojure.core.cache :as cache]
            [liberator.core :refer [defresource resource]]
            [liberator.dev :refer [wrap-trace]]
            [clojure.tools.logging :as log]))
;;; sync db
(sync-db 3 "dev")

(def ds-cache (atom (cache/lru-cache-factory {})))

(defn with-cache [cref item value-fn]
  (cache/lookup (if (cache/has? @cref item)
                  (swap! cref #(cache/hit % item))
                  (swap! cref #(cache/miss % item (value-fn item)))
                  ) item)
  )

(defn get-ds [ds-id]
  (with-cache ds-cache ds-id #(-> (k/select data_source (k/where {:id %}))
                                  first
                                  safe-mk-ds))
  )

(def common-opts {:available-media-types ["application/json"]})

(defn wrap-exception [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (log/error e "Exception handling request")
        (throw e))
      )))
;; ds checker middleware

;; handlers
(defn handle-exec-query [req ds-id]
  (let [ds (get-ds ds-id)
        params (:body req)
        res (exec-query ds params)]
    {:body res})
  )
;; resources

(defresource data-sources-list common-opts
  :allowed-methods [:get :post]
  :allowed? #(if-let [user-id (get-in % [:request :session :user :id])]
               {:user-id user-id})
  :post! #(let [ds-data (get-in %1 [:request :body])
                _ (mk-ds ds-data)
                user-id (:user-id %1)
                id (k/insert data_source (k/values (assoc ds-data :app_user_id user-id)))]
            {::id (first (vals id))})
  :post-redirect? (fn [ctx] {:location (format "/data-sources/%s" (::id ctx))})
  :handle-ok #(user-data-sources (:user-id %))
  )

(defresource data-sources-entry [id] common-opts
  :allowed-methods [:get :put :delete]
  :exists? (if-let [ds (first (k/select data_source (k/where {:id id})))]
             {:the-ds ds})
  ;; :allowed? #(let [ds (first (k/select data_source (k/where {:id id})))
  ;;                  user-id (get-in %1 [:request :session :user :id])]
  ;;              (if (= user-id (:app_user_id ds))
  ;;                {:the-ds ds}))
  :handle-ok #(:the-ds %)
  :delete! (fn [_] (k/delete data_source (k/where {:id id})))
  :put! #(k/update data_source (k/set-fields (get-in % [:request :body])) (k/where {:id id}))
  )

(defresource queries-list common-opts
  :allowed-methods [:get :post]
  :allowed? #(if-let [user-id (get-in % [:request :session :user :id])]
               {:user-id user-id})
  :post! #(let [{{data :body} :request user-id :user-id} %
                id (k/insert query (k/values (assoc data :app_user_id user-id)))]
            {::id (first (vals id))})
  :post-redirect? #({:location (format "/queries/%s" (::id %))})
  :handle-ok (k/select query))

(defresource queries-entry [id] common-opts
  :allowed-methods [:get :put :delete]
  :exists? (if-let [q (first (k/select query (k/fields [:sql]) (k/where {:id id})))]
             {:the-query q})
  :handle-ok #(:the-query %)
  :put! #(k/update query (k/set-fields (get-in % [:request :body])) (k/where {:id id}))
  :delete! (fn [_] (k/delete query (k/where {:id id}))))

(defroutes static
  (route/resources "/")
  (GET "/ping" [] (fn [req] (format "replied at %s" (Date.))))
  )

(defroutes app
  (GET "/" [] (slurp (io/resource "public/index.html")))

  (POST "/login" req (let [{user-name :userName pass :password} (:body req)
                           session (:session req)]
                       (try-let [user (login user-name pass)]
                                (if (some? user)
                                  {:body user :session (assoc session :user user)}
                                  {:status 401 :body "invalid user or password"})
                                (fn [e] {:status 500 :body (.getMessage e)}))))

  (GET "/logout" req (assoc (redirect "/") :session nil))

  (GET "/user" req (if-let [user (get-in req [:session :user])]
                     {:body user}
                     {:status 401 :body "user not logged in"}))

  (ANY "/data-sources" [] data-sources-list)
  (ANY "/data-sources/:id" [id] (data-sources-entry id))
  (ANY "/queries" [] queries-list)
  (ANY "/queries/:id" [id] (queries-entry id))

  (context "/ds/:ds-id" [ds-id]
           (POST "/exec-sql" req (let [raw-sql (get-in req [:body :raw-sql])
                                       ds (get-ds ds-id)]
                                   (try-let [r (execute ds raw-sql)]
                                            (if (number? r)
                                              {:body {:rowsAffected r}}
                                              {:body r})
                                            (fn [e] {:status 500 :body (.getMessage e)}))
                                   ))

           (POST "/exec-query" req (handle-exec-query req ds-id))

           (POST "/exec-query/:id" [id] (if-let [q (first (k/select query (k/fields [:sql]) (k/where {:id id})))]
                                          (let [r (execute (get-ds ds-id) (:sql q))]
                                            (if (number? r)
                                              {:body {:rowsAffected r}}
                                              {:body r})
                                            )
                                          {:status 404 :body "no such query exists!"}))

           (GET "/tables" req (if-let [ds (get-ds ds-id)]
                                (try-let [ts (tables ds)]
                                         {:body ts}
                                         (fn [e] {:status 500 :body (.getMessage e)}))
                                {:status 500 :body "default data source not available"}))

           (GET "/tables/:name" [name] (fn [req] {:body  (table-meta (get-ds ds-id) name)})))

  )


(defroutes all-routes
  static
  (-> app
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (wrap-trace :header :ui)
      (wrap-exception)
      (wrap-defaults (assoc site-defaults :security {:anti-forgery false}))))
;;

(defn in-dev? [] true)

(defn -main []
  (sync-db 3 "dev")
  (run-server (reload/wrap-reload #'all-routes) {:port 3000}))
