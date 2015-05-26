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
            [clojure.core.cache :as cache]))
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
                                  mk-ds))
  )

;; ds checker middleware

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

  (POST "/data-source" [] (fn [req]
                            (let [ds-data (:body req)
                                  ds-res (mk-ds ds-data)
                                  user-id (get-in req [:session :user :id])]
                              (if-let [ds (:datasource ds-res)]
                                (try-let [id (k/insert data_source (k/values (assoc ds-data :app_user_id user-id)))]
                                         {:body (first (vals id))}
                                         #({:status 500 :body (.getMessage %)}))
                                {:status 400 :body (:error ds-res)}
                                )
                              ))
        )

  (GET "/data-source" req (let [user-id (get-in req [:session :user :id])]
                            (try-let [r (user-data-sources user-id)]
                                     {:body r}
                                     #({:status 500 :body (.getMessage %)}))))
  (context "/ds/:ds-id" [ds-id]
           (POST "/execute" req (let [raw-sql (get-in req [:body :raw-sql])
                                      ds (get-ds ds-id)]
                                  (try-let [r (execute ds raw-sql)]
                                           (if (number? r)
                                             {:body {:rowsAffected r}}
                                             {:body r})
                                           (fn [e] {:status 500 :body (.getMessage e)}))
                                  ))

           (GET "/tables" req (if-let [ds (get-ds ds-id)]
                                (try-let [ts (tables ds)]
                                         {:body ts}
                                         (fn [e] {:status 500 :body (.getMessage e)}))
                                {:status 500 :body "default data source not available"}))

           (GET "/tables/:name" [name] (fn [req] {:body  (table-data (get-ds ds-id) name)})))

  )


(defroutes all-routes
  static
  (-> app
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (wrap-defaults (assoc site-defaults :security {:anti-forgery false}))))
;;

(defn in-dev? [] true)

(defn -main []
  (sync-db 3 "dev")
  (run-server (reload/wrap-reload all-routes) {:port 3000}))
