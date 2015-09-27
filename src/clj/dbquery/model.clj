;; the model definitions
(ns dbquery.model
  (:import [com.rinconj.dbupgrader DbUpgrader]
           [org.h2.jdbcx JdbcDataSource]
           [org.jasypt.util.text BasicTextEncryptor]
           )
  (:require [korma.db :refer :all]
            [clojure.tools.logging :as log]
            [korma.core :refer :all]
            [dbquery.conf :refer :all]
            [crypto.password.bcrypt :as password]
            [clojure.java.jdbc :as j]
            [dbquery.databases :as db]
            )
  )


(defn encrypt [str]
  (-> (doto (BasicTextEncryptor.)
        (.setPassword (conf :secret-key)))
      (.encrypt str))
  )

(defn decrypt [str]
  (-> (doto (BasicTextEncryptor.)
        (.setPassword (conf :secret-key)))
      (.decrypt str))
  )
;;(password/encrypt "admin")

(def ds (delay (doto (JdbcDataSource.)
                 (.setUrl (str "jdbc:h2:" (db-conf :db)))
                 (.setUser (db-conf :user))
                 (.setPassword (db-conf :password)))))

(defn sync-db
  ([version env]
   (do
     (log/info "starting db upgrade:" version env)
     (-> (DbUpgrader. (force ds) env)
         (.syncToVersion version false false))
     (log/info "db upgrade complete")
     ))
  ([env] (sync-db 7 env)))

(defdb appdb (h2 db-conf))

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
                   ds)))
  )

(defentity app_user
  (entity-fields :id :nick :full_name :active)
  (many-to-many data_source :user_data_source)
  )

(defentity query
  (entity-fields :id :name :description)
  (belongs-to app_user)
  (many-to-many data_source :data_source_query)
  )

(defentity ds_table
  (entity-fields :id :name :type)
  (belongs-to data_source)
  )

(defentity ds_column
  (entity-fields :id :name :data_type :type_name :size :digits :nullable
                 :is_pk :is_fk :fk_table :fk_column)
  (belongs-to ds_table {:fk :table_id})
  )

(defn login [user pass]
  (if-let [found-user (first (select app_user (fields :password) (where {:nick user})))]
    (if (password/check pass (:password found-user))
      (dissoc found-user :password)))
  )

(defn user-data-sources [user-id]
  (exec-raw ["SELECT d.id, d.name FROM DATA_SOURCE d
WHERE APP_USER_ID = ? OR EXISTS(SELECT 1 FROM USER_DATA_SOURCE ud
WHERE ud.DATA_SOURCE_ID=d.ID AND ud.APP_USER_ID=?)" [user-id user-id]] :results)
  )

(defn get-query [id]
  (with-open [con (.getConnection (force ds))]
    (let [ps (.prepareStatement con "SELECT * FROM QUERY WHERE ID=?")]
      (.setLong ps 1 (Long/parseLong id))
      (-> ps
          .executeQuery
          db/read-as-map
          first))
    )
  )

(defn ds-queries [db-id]
  (db/execute {:datasource (force ds)} "select q.* from query q
join data_source_query dq on dq.query_id = q.id where dq.data_source_id =?"
              {:rs-reader db/read-as-map :args [db-id]}))

(defn query-assocs [qid]
  (db/execute {:datasource (force ds)} "select ds.id, ds.name, q.query_id
from data_source ds left join data_source_query q on q.data_source_id = ds.id
and q.query_id = ?" {:rs-reader db/read-as-map :args [qid]}))

(defn assoc-query-datasource [ds-id q-id]
  (db/execute {:datasource (force ds)}
              "insert into data_source_query(data_source_id, query_id)
 values(?,?)" {:args [ds-id q-id]} )
  )

(defn dissoc-query-datasource [ds-id q-id]
  (db/execute {:datasource (force ds)}
              "delete data_source_query where data_source_id=?
and query_id=?" {:args [ds-id q-id]} )
  )


(defn sync-table-cols [table-id cols]
  (delete ds_column
          (where {:table_id table-id}))
  (insert ds_column
          (values (for [c cols]
                    (-> c
                        (dissoc :table_name)
                        (assoc :table_id table-id)
                        )))))

(defn sync-table-meta [ds-id table-meta]
  (log/info "syncing table metadata:" ds-id (prn-str table-meta))
  (let [table-id
        (or (-> (select ds_table (where {:name (:name table-meta)
                                         :data_source_id ds-id}))
                first :id)
            (-> (insert ds_table
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
            old-tables (into {} (for [t (select ds_table (fields ::* :name :id)
                                                (where {:data_source_id ds-id}))]
                                  [(:name t) (:id t)]))]
        (doseq [[name table] new-tables :when (not (contains? old-tables name)) ]
          (log/info "syncing new table metadata:" name)
          (sync-table-meta ds-id (assoc table :columns (db/table-cols ds name))))
        (doseq [[name id] old-tables :when (not (contains? new-tables name))]
          (log/info "deleting removed table metadata:" name)
          (delete ds_column (where {:table_id id}))
          (delete ds_table (where {:id id})))))
    tables))

;; (defn get-table-joins [ds-id table]
;;   (->>(select
;;        ds_column
;;        (fields ::* [:fk_table :parent-table] [:fk_column :parent-col]
;;                [:name :child-col])
;;        (with ds_table (fields ::*)
;;              (where {:name table}))
;;        (where {:data_source_id ds-id :is_fk true}))
;;       (group-by :parent-table)
;;       (map (fn [key cols]
;;              (assoc key :cols (map #(select-keys % [:child-col :parent-col]) cols))))))
