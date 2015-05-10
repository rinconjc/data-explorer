;; the model definitions
(ns dbquery.model
  (:import [com.rinconj.dbupgrader DbUpgrader]
           [org.h2.jdbcx JdbcDataSource])
  (:require [korma.db]
             [dbquery.conf :refer :all])
  )

(def ^:private ds (doto JdbcDataSource.
          (.setURL (db-conf :url))
          (.setUser (db-conf :user))
          (.setPassword (db-conf :password))))

(-> (DbUpgrader. ds "dev")
  (.syncToVersion 1 true true))

(defdb appdb (h2 db-conf))

(defentity data_source
  (entity-fields :id :name :user_name :password :url)  
  )
