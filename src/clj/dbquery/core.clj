(ns dbquery.core
  (:gen-class)
  (:require [cheshire.generate :refer [add-encoder]]
            [clojure.core.cache :as cache]
            [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [compojure.core :refer :all]
            [compojure.route :as route]
            [crypto.password.bcrypt :as password]
            [dbquery.databases :as db :refer :all]
            [dbquery.model :as model :refer :all]
            [dbquery.utils :refer :all]
            [korma.core :as k]
            [liberator.core :refer [defresource]]
            [liberator.dev :refer [wrap-trace]]
            [org.httpkit.server :refer [run-server]]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer :all]
            [ring.middleware.multipart-params :as mp]
            [ring.middleware.reload :as reload]
            [ring.util.response :refer [redirect]]
            [clojure.string :as str])
  (:import java.awt.Desktop
           java.io.FileReader
           java.net.URI
           java.text.SimpleDateFormat
           java.util.Date))

;; custom json encoder for dates
(defn- date-time-encoder [fmt]
  (fn [d jsonGenerator]
    (let [sdf (SimpleDateFormat. fmt)]
      (.writeString jsonGenerator (.format sdf d)))))

(defn- add-encoders []
  (add-encoder (Class/forName "[B") #(.writeString %2 "<binary>"))
  (add-encoder java.sql.Blob #(.writeString %2 "<binary>"))
  (add-encoder java.util.Date (date-time-encoder "dd/MM/yyyy"))
  (add-encoder java.sql.Date (date-time-encoder "dd/MM/yyyy"))
  (add-encoder java.sql.Timestamp (date-time-encoder "dd/MM/yyyy HH:mm:ss"))
  (add-encoder java.sql.RowId #(.writeString %2 %1))
  (add-encoder java.sql.Array #(.writeString %2 (str/join ", " (.getArray %1))))
  (try
    (add-encoder (Class/forName "oracle.sql.ROWID") #(.writeString %2 (.stringValue %1)))
    (catch Exception e
      (log/warn "failed registering encoder for oracle.sql.ROWID. Add Oracle JDBC driver to the classpath if using Oracle DB"))))

(def ds-cache (atom (cache/lru-cache-factory {})))

(def session-count (atom 0))

(defn with-cache [cref item value-fn]
  (cache/lookup (if (cache/has? @cref item)
                  (swap! cref #(cache/hit % item))
                  (swap! cref #(cache/miss % item (value-fn item))))
                item))

(defn expire-cache [cache-ref entry-id]
  (log/info "expiring cache entry:" entry-id)
  (when-let [ds (cache/lookup @cache-ref entry-id)]
    (swap! cache-ref cache/evict entry-id)
    (future (-> ds :datasource .close))))

(defn get-ds [ds-id]
  (with-cache ds-cache ds-id
    #(let [ds-details (first (k/select data_source (k/fields [:password]) (k/where {:id %})))]
       {:datasource (mk-ds ds-details)
        :schema (:schema ds-details)
        :dbms (:dbms ds-details)})))

(def common-opts {:available-media-types ["application/json"]})

(defn wrap-exception [handler]
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (log/error e "Exception handling request")
        {:status 500 :body (assoc (ex-data e )
                                  :error (or (.getMessage e) "Unknown internal error"))}))))

(defn current-user [ctx]
  (if-let [user-id (get-in ctx [:request :session :user :id])]
    {:user-id user-id}))

;; ds checker middleware

;; handlers
(defn handle-exec-query [req ds-id]
  (let [ds (get-ds ds-id)
        params (:body req)
        res (exec-query ds params)]
    {:body res}))

(def download-jobs (atom {}))

(defn create-download [req ds-id]
  (let [id (rand-int 10000)]
    (swap! download-jobs assoc id (future (handle-exec-query req ds-id)))
    {:body {:download-id id}}))

(defn handle-login [req]
  (log/info "handling login...")
  (let [{user-name :userName pass :password} (:body req)
        session (:session req)]
    (try-let
     [user (login user-name pass)]
     (if (some? user)
       {:body user
        :session (assoc session
                        :user (assoc user :session-id (swap! session-count inc)))}
       {:status 401 :body "invalid user or password"})
     (fn [e]
       (log/error e "failed login")
       {:status 500 :body (.getMessage e)}))))

(defn read-csv [file separator has-header]
  (let [csv (csv/read-csv (FileReader. file) :separator separator)
        first (first csv)]
    {:header (if has-header first (for [i (range  (count first))] (str "Col" i)))
     :rows (if has-header (rest csv) csv)}))

(defn handle-file-upload [{{file :file separator :separator has-header :hasHeader} :params}]
  ;; extract
  (log/info "separator:" separator)
  (let [{header :header rows :rows} (read-csv (file :tempfile) (.charAt separator 0) (some? has-header))]
    {:body {:header header :rows (take 4 rows) :file (.getName (file :tempfile))}}))

(defn query-id [q-id ds-id req]
  (str q-id  "/" ds-id "/" (get-in req [:session :user :session-id])))

(defn parse-sql-meta [sql]
  (let [[query-name meta] (rest (re-find #"(?m)\s*--\s*#\s*([^@\n]+)(@.+)?$" sql))]
    (when query-name
      {:name query-name
       :label (or (and meta (second (re-find #"@label:([\w]+)" meta))) "default")
       :shared (or (nil? (and meta (re-find #"@private" meta))) false)})))

(defn handle-exec-sql [req ds-id]
  (let [{:keys [sql] :as opts} (:body req)
        ds (get-ds ds-id)
        r (execute ds sql (update opts :id query-id ds-id req))]
    (future
      (try
        (when-let [meta (parse-sql-meta sql)]
          (model/save-query (assoc meta :sql sql :app_user_id (:user-id (current-user req)))))
        (catch Exception e
          (log/error e "failed saving query"))))
    {:body r}))

(defn handle-exec-query-by-id [id ds-id]
  (if-let [q (first (k/select query (k/fields [:sql]) (k/where {:id id})))]
    (let [r (execute (get-ds ds-id) (:sql q))]
      {:body r})
    {:status 404 :body "no such query exists!"}))

(defn handle-list-tables [req ds-id]
  (let [tables  (k/select ds_table (k/where {:data_source_id ds-id}))]
    (if (empty? tables)
      (let [ds (get-ds ds-id)]
        (log/info "table metadata not found. fallback to quick table list...")
        (future (load-metadata ds ds-id))
        (get-tables ds))
      (if (= "true" (get-in req [:params :refresh]))
        (sync-tables (get-ds ds-id) ds-id)
        tables))))

(defn handle-table-meta [{{refresh :refresh} :params} ds-id table]
  (if-let [table-id (-> (k/select ds_table (k/fields ::* :id)
                                  (k/where {:name table})) first :id)]
    {:columns (if (= "true" refresh)
                (let [cols (table-cols (get-ds ds-id) table)]
                  (sync-table-cols table-id cols)
                  cols)
                (k/select ds_column (k/where {:table_id table-id}) (k/order :id)))}))

(defn handle-data-import [ds-id {{:keys [file separator hasHeader dest]} :body}]
  (let [ds (get-ds ds-id)
        table (if (= "_" (:table dest))
                (create-table ds (dest :newTable) (dest :columns) nil)
                (dest :table))
        filePath (str (System/getProperty "java.io.tmpdir") "/" file)
        data (read-csv (java.io.File. filePath) (.charAt separator 0) hasHeader)
        result (load-data ds table data (dest :mappings))]
    {:body result}))

(defn handle-download [ds-id query]
  (log/info "downloading query output" query)
  (let [ds (get-ds ds-id)
        os (java.io.PipedOutputStream.)
        is (java.io.PipedInputStream. os)
        writer (java.io.OutputStreamWriter. os)]
    (future
      (try
        (db/execute ds query {:rs-reader #(db/rs-to-csv %1 writer %2)})
        (catch Exception e (log/error "failed exporting to CSV" e)))
      (.close writer))
    {:body is
     :headers { ;;"Transfer-Encoding" "chunked"
               "Content-Disposition" "attachment; filename=data.csv"} }))

(defn with-body [b]
  (cond
    (or (instance? Number b) (instance? Boolean b)) {:body {:result b}}
    (nil? b) {:status 404}
    true {:body b}))


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
  :handle-ok #(user-data-sources (:user-id %)))

(defresource data-sources-entry [id] common-opts
  :allowed-methods [:get :put :delete]
  :exists? (if-let [ds (first (k/select data_source (k/where {:id id})))]
             {:the-ds ds})
  ;; :allowed? #(let [ds (first (k/select data_source (k/where {:id id})))
  ;;                  user-id (get-in %1 [:request :session :user :id])]
  ;;              (if (= user-id (:app_user_id ds))
  ;;                {:the-ds ds}))
  :handle-ok #(:the-ds %)
  :new? false
  :delete! (fn [_]
             (k/delete data_source (k/where {:id id}))
             (expire-cache ds-cache id))
  :put! (fn [{{ds-data :body} :request}]
          (mk-ds ds-data)
          (k/update data_source (k/set-fields ds-data) (k/where {:id id}))
          (expire-cache ds-cache id)
          nil))

(defresource queries-list common-opts
  :allowed-methods [:get :post]
  :allowed? current-user
  :post! #(let [{{data :body} :request user-id :user-id} %
                id (first (vals (k/insert query (k/values (assoc data :app_user_id user-id)))))]
            {::id id})
  :post-redirect? (fn [ctx] {:location (format "/queries/%s" (::id ctx))})
  :handle-ok (k/select query))

(defresource queries-entry [id] common-opts
  :allowed-methods [:get :put :delete]
  :exists? (if-let [q (get-query id)]
             {:the-query q})
  :new? false
  :respond-with-entity? true
  :handle-ok #(:the-query %)
  :put! #(k/update query (k/set-fields (get-in % [:request :body]))
                   (k/where {:id id}))
  :delete! (fn [_] (k/delete query (k/where {:id id}))))

(defresource users common-opts
  :allowed-methods [:get :post]
  :allowed? #(or (= :post (-> % :request :request-method))
                 (current-user %))
  :post! #(let [data (-> % :request :body)
                id (-> app_user (k/insert (k/values (update data :password password/encrypt)))
                       vals first)]
            {::id id})
  :handle-ok (k/select app_user))

(defroutes static
  (route/resources "/")
  (GET "/ping" [] (fn [req] (format "replied at %s" (Date.)))))

(defroutes api
  (GET "/" [] (slurp (io/resource "public/index.html")))

  (POST "/login" req (handle-login req))
  (wrap-exception (mp/wrap-multipart-params
                   (POST "/upload" req (handle-file-upload req))))
  (GET "/logout" req (assoc (redirect "/") :session nil))
  (GET "/user" req (if-let [user (get-in req [:session :user])]
                     {:body user}
                     {:status 401 :body "user not logged in"}))

  (ANY "/users" [] users)

  (ANY "/data-sources" [] data-sources-list)
  (ANY "/data-sources/:id" [id] (data-sources-entry id))
  (ANY "/queries" [] queries-list)
  (ANY "/queries/:id" [id] (queries-entry id))
  (PUT "/queries/:id/data-source/:ds" [id ds]
       (with-body (assoc-query-datasource ds id)))
  (POST "/queries/:id/data-sources" [id]
        #(with-body (assoc-query-datasource id (get-in % [:body :ds-ids]))))
  (DELETE "/queries/:id/data-sources" [id]
          #(with-body (dissoc-query-datasource id (get-in % [:body :ds-ids]))))
  (GET "/queries/:id/data-source" [id] (with-body (query-assocs id)))

  (context "/ds/:ds-id" [ds-id]
           (POST "/exec-sql" req (handle-exec-sql req ds-id))
           (POST "/download" req (create-download req ds-id))
           (POST "/cancel-sql/:id" [id] (fn[req]
                                          (cancel-query (query-id id ds-id req))
                                          {:status 201}))
           (GET "/tables" req (with-body (handle-list-tables req ds-id)))
           (GET "/tables/:name" [name] (fn [req]
                                         (with-body (handle-table-meta req ds-id name))))
           (GET "/data-types" req (with-body (data-types (get-ds ds-id))))
           (POST "/import-data" req (handle-data-import ds-id req))
           (GET "/queries" req (with-body (ds-queries ds-id)))
           ;; (GET "/related/:tables" [tables] (with-body (get-related-tables ds-id (s/split tables #",\s*"))))
           (GET "/download" req (handle-download ds-id (get-in req [:params :query])))))

(defroutes app
  static
  (-> api
      (wrap-exception)
      (wrap-json-response)
      (wrap-json-body {:keywords? true})
      (wrap-trace :header :ui)
      (wrap-defaults (assoc site-defaults :security {:anti-forgery false}))))
;;
(defn start-server [port]
  (model/upgrade-db)
  (add-encoders)
  (run-server (reload/wrap-reload #'app)
              {:port port :thread 50}))

(defn -main [& args]
  (let [port (or (some-> args first Integer/parseInt) 3001)]
    (start-server port)
    (if (Desktop/isDesktopSupported)
      (try
        (doto (Desktop/getDesktop)
          (.browse (URI. (str "http://localhost:" port))))
        (catch Exception e
          (log/warn "failed to open browser" e))))))
