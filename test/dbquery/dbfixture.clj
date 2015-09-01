(ns dbquery.dbfixture
  (:require [dbquery.databases :refer :all]
            [dbquery.model :refer :all]
            [korma.core :as k]))

(defn dummy-ds [] (safe-mk-ds {:dbms "H2" :url  "mem:test;DB_CLOSE_DELAY=0" :user_name  "sa" :password "sa"}))
(defn fixture [f]
  (def con (.getConnection (:datasource (dummy-ds))))
  (f)
  (.close con)
  )

(defn model-fixture [f]
  (with-open [con (.getConnection (force ds))]
    (def dsinfo {:dbms "H2" :url "mem:ds-1" :user_name "sa" :password "sa"})
    (def ds1 (mk-ds dsinfo))
    (with-open [con2 (.getConnection ds1)]
      (execute {:datasource ds1} "create table tablea(id int, name varchar(20))")
      (sync-db "test")
      (println "creating test datasource:")
      (if (nil? (first (k/select data_source (k/where {:id 1}))))
        (k/insert data_source (k/values (merge {:name "test" :id 1} dsinfo))))
      (f))
    )
  )
