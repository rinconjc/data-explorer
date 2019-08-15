(ns dbquery.dbfixture
  (:require [dbquery.databases :refer :all]
            [dbquery.model :refer :all]
            [korma.core :as k]))

(System/setProperty "conf" "./resources/sample-conf.edn")

(def counter (atom 0))
(def ds-ref (atom nil))

(defn dummy-ds []
  @ds-ref)

(defn fixture [f]
  (let [db (safe-mk-ds {:dbms "H2"
                        :url  (format "mem:test%s;DB_CLOSE_DELAY=120" (System/currentTimeMillis))
                        :user_name  "sa" :password "sa"})]
    (reset! ds-ref db)
    (println "dummy ds connection active:" db)
    (try
      (f)
      (finally
        (.close (:datasource db))
        (println "dummy ds connection closed")))))

(defn model-fixture [f]
  (with-open [con (.getConnection (force ds))]
    (let [dsinfo {:dbms "H2" :url (str "mem:ds" (swap! counter inc) ";DB_CLOSE_DELAY=-1")
                  :user_name "sa" :password "sa"}]
      (with-open [ds1 (mk-ds dsinfo)
                  con2 (.getConnection ds1)]
        (execute {:datasource ds1} "create table tablea(id int, name varchar(20))")
        (sync-db "test")
        (println "creating test datasource:")
        (if (nil? (first (k/select data_source (k/where {:id 1}))))
          (k/insert data_source (k/values (merge {:name "test" :id 1} dsinfo))))
        (f)))))
