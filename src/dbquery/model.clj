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

(defn sync-table-meta [ds-id table-meta]
  "creates or updates the table metadata in the specified datasource"
  (log/info "syncing table metadata:" ds-id (prn-str table-meta))
  (def table-id
    (if-let [t (first (select ds_table (where {:name (:name table-meta) :data_source_id ds-id})))]
      (do (delete ds_column (where {:table_id (:id t)}))
          (:id t))
      (-> (insert ds_table (values (-> table-meta
                                       (select-keys [:name :type])
                                       (assoc :data_source_id ds-id)) ))
          vals first)))
  (insert ds_column (values (for [c (:columns table-meta)]
                              (-> c
                                  (dissoc :table_name)
                                  (assoc :table_id table-id)
                                  ))))
  )

(defn load-metadata [ds ds-id]
  (doseq [tm (db/db-meta ds)]
    (try
      (sync-table-meta ds-id tm)
      (catch Exception e
        (log/error e "failed syncing table " tm)))
    )
  )

(defn sync-tables [ds ds-id]
  (let [tables  (db/get-tables ds)]
    ;; TODO sync tables
    tables
    )
  )
