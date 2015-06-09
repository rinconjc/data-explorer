;; the model definitions
(ns dbquery.model
  (:import [com.rinconj.dbupgrader DbUpgrader]
           [org.h2.jdbcx JdbcDataSource])
  (:require [korma.db :refer :all]
            [clojure.tools.logging :as log]
            [korma.core :refer :all]
            [dbquery.conf :refer :all]
            [crypto.password.bcrypt :as password]
            [clojure.java.jdbc :as j]
            )
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
         (.syncToVersion version true true))
     (log/info "db upgrade complete")
     ))
  ([env] (sync-db 3 env)))

(defdb appdb (h2 db-conf))

(declare data_source app_user query query_params)

(defentity data_source
  (entity-fields :id :name :dbms :user_name :password :url :app_user_id)
  (belongs-to app_user)
  (many-to-many query :data_source_query)
  (many-to-many app_user :user_data_source)
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
