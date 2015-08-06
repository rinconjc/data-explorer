(ns dbquery.core
  (:import [java.util Date]
           [java.io FileReader]
           )
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            ;; [compojure.handler :refer [site]]
            [ring.middleware.json :refer :all]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.reload :as reload]
            [ring.middleware.multipart-params :as mp]
            [org.httpkit.server :refer [run-server]]
            [dbquery.databases :refer :all]
            [dbquery.utils :refer :all]
            [clojure.java.io :as io]
            [korma.core :as k]
            [dbquery.model :refer :all]
            [clojure.core.cache :as cache]
            [liberator.core :refer [defresource resource]]
            [liberator.dev :refer [wrap-trace]]
            [clojure.tools.logging :as log]
            [clojure.data.csv :as csv]))

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

(defn expire-cache [cache-ref entry-id]
  (log/info "expiring cache entry:" entry-id)
  (swap! cache-ref #(cache/evict % entry-id))
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

(defn handle-login [req]
  (let [{user-name :userName pass :password} (:body req)
        session (:session req)]
    (try-let [user (login user-name pass)]
             (if (some? user)
               {:body user :session (assoc session :user user)}
               {:status 401 :body "invalid user or password"})
             (fn [e] {:status 500 :body (.getMessage e)}))))

(defn handle-file-upload [{{file :file separator :separator has-header :hasHeader} :params}]
  ;; extract
  (log/info "separator:" separator)
  (let [csv (csv/read-csv (FileReader. (file :tempfile)) :separator (.charAt separator 0))
        first (first csv)
        has-header? (Boolean/parseBoolean has-header)
        header (if has-header? first (for [i (range  (count first))] (str "Col" i)))
        rows (if has-header? (rest csv) csv)
        ]
    {:body {:header header :rows (take 4 rows) :file (.getAbsolutePath (file :tempfile))}})
  )

(defn handle-exec-sql [req ds-id]
  (let [raw-sql (get-in req [:body :raw-sql])
        ds (get-ds ds-id)]
    (let [opts (into {} (for [[k v] (:params req) :when (#{:offset :limit} k)] [k (Integer. v)]))
          r (execute ds raw-sql opts)]
      (if (number? r)
        {:body {:rowsAffected r}}
        {:body {:data  r}})
      )
    ))

(defn handle-exec-query-by-id [id ds-id]
  (if-let [q (first (k/select query (k/fields [:sql]) (k/where {:id id})))]
    (let [r (execute (get-ds ds-id) (:sql q))]
      (if (number? r)
        {:body {:rowsAffected r}}
        {:body r})
      )
    {:status 404 :body "no such query exists!"}))

(defn handle-list-tables [ds-id]
  (if-let [ds (get-ds ds-id)]
    (try-let [ts (tables ds)]
             {:body ts}
             (fn [e] {:status 500 :body (.getMessage e)}))
    {:status 500 :body "default data source not available"}))

(defn handle-data-types [ds-id]
  (let [ds (get-ds ds-id)]
    {:body  (data-types ds)}))

(defn handle-data-import [ds-id {{file-details :fileDetails dest :dest} :body}]
  (let [ds (get-ds ds-id)
        table (if (= "_" (dest :table))
                (create-table ds (dest :newTable) (dest :columns) nil)
                (dest :table))
        result (load-data ds table file-details (dest :mappings))]
    {:body result}
    )
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
  :delete! (fn [_]
             (k/delete data_source (k/where {:id id}))
             (expire-cache ds-cache id))
  :put! (fn [{{ds-data :body} :request}]
          (k/update data_source (k/set-fields ds-data) (k/where {:id id}))
          (expire-cache ds-cache id)
          )
  )

(defresource queries-list common-opts
  :allowed-methods [:get :post]
  :allowed? #(if-let [user-id (get-in % [:request :session :user :id])]
               {:user-id user-id})
  :post! #(let [{{data :body} :request user-id :user-id} %
                id (k/insert query (k/values (assoc data :app_user_id user-id)))]
            {::id (first (vals id))})
  :post-redirect? (fn [ctx] {:location (format "/queries/%s" (::id ctx))})
  :handle-ok (k/select query))

(defresource queries-entry [id] common-opts
  :allowed-methods [:get :put :delete]
  :exists? (if-let [q (get-query id)]
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

  (POST "/login" req (handle-login req))
  (mp/wrap-multipart-params (POST "/upload" req (handle-file-upload req)))
  (GET "/logout" req (assoc (redirect "/") :session nil))
  (GET "/user" req (if-let [user (get-in req [:session :user])]
                     {:body user}
                     {:status 401 :body "user not logged in"}))

  (ANY "/data-sources" [] data-sources-list)
  (ANY "/data-sources/:id" [id] (data-sources-entry id))
  (ANY "/queries" [] queries-list)
  (ANY "/queries/:id" [id] (queries-entry id))

  (context "/ds/:ds-id" [ds-id]
           (POST "/exec-sql" req (handle-exec-sql req ds-id))
           (POST "/exec-query" req (handle-exec-query req ds-id))
           (POST "/exec-query/:id" [id] (handle-exec-query-by-id id))
           (GET "/tables" req (handle-list-tables ds-id))
           (GET "/tables/:name" [name] (fn [req] {:body  (table-meta (get-ds ds-id) name)}))
           (GET "/data-types" req (handle-data-types ds-id))
           (POST "/import-data" req (handle-data-import ds-id req))
           )
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

(defn -main []
  (sync-db "dev")
  (run-server (reload/wrap-reload #'all-routes) {:port 3000}))
