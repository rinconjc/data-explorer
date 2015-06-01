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
            [liberator.core :refer [defresource resource]]))
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

  (ANY "/data-sources" [] (resource :allowed-methods [:get :post]
                                    :allowed? #(if-let [user-id (get-in % [:request :session :user :id])]
                                                 {:user-id user-id})
                                    :post! #(let [ds-data (get-in %1 [:body :request])
                                                  _ (mk-ds ds-data)
                                                  user-id (:user-id %1)
                                                  id (k/insert data_source (k/values (assoc ds-data :app_user_id user-id)))]
                                              {::id (first (vals id))})
                                    :post-redirect? #({:location (format "/data-sources/%s" (::id %))})
                                    :handle-ok #(let [user-id (get-in % [:request :session :user :id])]
                                                  (user-data-sources user-id))
                                    :handle-exception #(.getMessage (:exception %))))

  (ANY "/data-sources/:id" [id] (resource
                                 :allowed-methods [:get :put :delete]
                                 :exists? (if-let [ds (first (k/select data_source (k/where {:id id})))]
                                            {:the-ds ds})
                                 :allowed? #(let [user-id (get-in %1 [:request :session :user :id])]
                                              (= user-id (get-in %1 [:the-ds  :app_user_id])))
                                 :handle-ok #(:the-ds %)
                                 :delete! (k/delete data_source (k/where {:id id}))
                                 :put! #(k/update data_source (k/set-fields (get-in % [:request :body])) (k/where {:id id}))
                                 ))

  (context "/ds/:ds-id" [ds-id]
           (POST "/execute" req (let [raw-sql (get-in req [:body :raw-sql])
                                      ds (get-ds ds-id)]
                                  (try-let [r (execute ds raw-sql)]
                                           (if (number? r)
                                             {:body {:rowsAffected r}}
                                             {:body r})
                                           (fn [e] {:status 500 :body (.getMessage e)}))
                                  ))
           (POST "/exec-query" req (let [ds (get-ds ds-id)
                                         params (:body req)]
                                     (apply (partial exec-query ds) params)))

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
      (wrap-defaults (assoc site-defaults :security {:anti-forgery false}))))
;;

(defn in-dev? [] true)

(defn -main []
  (sync-db 3 "dev")
  (run-server (reload/wrap-reload all-routes) {:port 3000}))
