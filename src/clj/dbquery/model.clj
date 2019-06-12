;; the model definitions
(ns dbquery.model
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [crypto.password.bcrypt :as password]
            [dbquery
             [conf :refer :all]
             [databases :as db]]
            [korma
             [core :as k :refer [defentity transform entity-fields belongs-to
                                 many-to-many prepare fields exec-raw values]]
             [db :refer :all]])
  (:import com.rinconj.dbupgrader.DbUpgrader
           org.h2.jdbcx.JdbcDataSource
           org.jasypt.util.text.BasicTextEncryptor))

(defn encrypt [str]
  (-> (doto (BasicTextEncryptor.)
        (.setPassword (@conf :secret-key)))
      (.encrypt str)))

(defn decrypt [str]
  (-> (doto (BasicTextEncryptor.)
        (.setPassword (@conf :secret-key)))
      (.decrypt str)))
;;(password/encrypt "admin")

(def ds (delay (doto (JdbcDataSource.)
                 (.setUrl (str "jdbc:h2:" (@db-conf :db)))
                 (.setUser (@db-conf :user))
                 (.setPassword (@db-conf :password)))))

(defn sync-db
  ([version env]
   (log/info "starting db upgrade:" version env)
   (-> (DbUpgrader. (force ds) env)
       (.syncToVersion version false false))
   (log/info "db upgrade complete")
   (defdb appdb (h2 @db-conf)))
  ([env] (sync-db 7 env)))

(declare data_source app_user query query_params)

(defentity data_source
  (entity-fields :id :name :dbms :user_name :url :app_user_id :schema)
  (belongs-to app_user)
  (many-to-many query :data_source_query)
  (many-to-many app_user :user_data_source)
  (prepare (fn [{pass :password :as ds}]
             (assoc ds :password (encrypt pass))))
  (transform (fn [{pass :password :as ds}]
               (if (some? pass) (assoc ds :password (decrypt pass))
                   ds))))

(defentity app_user
  (entity-fields :id :nick :full_name :active)
  (many-to-many data_source :user_data_source))

(defentity query
  (entity-fields :id :name :description)
  (belongs-to app_user)
  (many-to-many data_source :data_source_query))

(defentity ds_table
  (entity-fields :id :name :type)
  (belongs-to data_source))

(defentity ds_column
  (entity-fields :id :name :data_type :type_name :size :digits :nullable
                 :is_pk :is_fk :fk_table :fk_column)
  (belongs-to ds_table {:fk :table_id}))

(defn login [user pass]
  (if-let [found-user (first (k/select app_user (fields :password) (k/where {:nick user})))]
    (if (password/check pass (:password found-user))
      (dissoc found-user :password))))

(defn user-data-sources [user-id]
  (exec-raw ["SELECT d.id, d.name FROM DATA_SOURCE d
WHERE APP_USER_ID = ? OR EXISTS(SELECT 1 FROM USER_DATA_SOURCE ud
WHERE ud.DATA_SOURCE_ID=d.ID AND ud.APP_USER_ID=?)" [user-id user-id]] :results))

(defn get-query [id]
  (with-open [con (.getConnection (force ds))]
    (let [ps (.prepareStatement con "SELECT * FROM QUERY WHERE ID=?")]
      (.setLong ps 1 (Long/parseLong id))
      (-> ps
          .executeQuery
          db/read-as-map
          first))))

(defn ds-queries [db-id]
  (-> (db/execute {:datasource (force ds)} "select q.* from query q
join data_source_query dq on dq.query_id = q.id where dq.data_source_id =?"
                  {:rs-reader db/read-as-map :args [db-id]})
      :data))

(defn query-assocs [qid]
  (-> (db/execute {:datasource (force ds)} "select ds.id, ds.name, q.query_id
from data_source ds left join data_source_query q on q.data_source_id = ds.id
and q.query_id = ?" {:rs-reader db/read-as-map :args [qid]})
      :data))

(defn assoc-query-datasource [q-id ds-ids]
  (apply jdbc/insert! {:datasource (force ds)}
         "data_source_query" ["data_source_id" "query_id"]
         (for [id ds-ids] [id q-id])))

(defn dissoc-query-datasource [q-id ds-ids]
  (jdbc/execute! {:datasource (force ds)}
                 (cons "delete data_source_query where data_source_id=?
and query_id=?" (for [id ds-ids] [id q-id])) :multi? true))


(defn sync-table-cols [table-id cols]
  (k/delete ds_column
          (k/where {:table_id table-id}))
  (let [rows (for [c cols] (-> c (dissoc :table_name) (assoc :table_id table-id)))
        rows (cons (merge {:is_fk false :fk_table nil :fk_column nil}
                          (first rows)) (rest rows))]
    (k/insert ds_column (values rows))))

(defn sync-table-meta [ds-id table-meta]
  (log/info "syncing table metadata:" ds-id (prn-str table-meta))
  (let [table-id
        (or (-> (k/select ds_table (k/where {:name (:name table-meta)
                                         :data_source_id ds-id}))
                first :id)
            (-> (k/insert ds_table
                        (values (-> table-meta
                                    (select-keys [:name :type])
                                    (assoc :data_source_id ds-id)) ))
                vals first))]
    (sync-table-cols table-id (:columns table-meta))))

(defn load-metadata [ds ds-id]
  (doseq [tm (db/db-meta ds)]
    (try
      (sync-table-meta ds-id tm)
      (catch Exception e
        (log/error e "failed syncing table " tm)))))

(defn sync-tables [ds ds-id]
  (let [tables  (db/get-tables ds)]
    (future
      (let [new-tables (into {} (for [t tables] [(:name t) t]))
            old-tables (into {} (for [t (k/select ds_table (fields ::* :name :id)
                                                (k/where {:data_source_id ds-id}))]
                                  [(:name t) (:id t)]))]
        (doseq [[name table] new-tables :when (not (contains? old-tables name)) ]
          (log/info "syncing new table metadata:" name)
          (sync-table-meta ds-id (assoc table :columns (db/table-cols ds name))))
        (doseq [[name id] old-tables :when (not (contains? new-tables name))]
          (log/info "deleting removed table metadata:" name)
          (k/delete ds_column (k/where {:table_id id}))
          (k/delete ds_table (k/where {:id id})))))
    tables))

;; (defn get-table-joins [ds-id table]
;;   (->>(k/select
;;        ds_column
;;        (fields ::* [:fk_table :parent-table] [:fk_column :parent-col]
;;                [:name :child-col])
;;        (with ds_table (fields ::*)
;;              (k/where {:name table}))
;;        (k/where {:data_source_id ds-id :is_fk true}))
;;       (group-by :parent-table)
;;       (map (fn [key cols]
;;              (assoc key :cols (map #(k/select-keys % [:child-col :parent-col]) cols))))))
