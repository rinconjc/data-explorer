(ns dbquery.model-test
  (:require [clojure.test :refer :all]
            [dbquery.model :refer :all]
            [dbquery.databases :refer :all]
            [korma.core :as k]))

(sync-db 2 "test")

(deftest test-model
  (testing "create datasource"
    (def r (k/insert data_source (k/values {:name "test" :dbms "H2" :url "mem:t1;DB_CLOSE_DELAY=-1" :user_name "sa" :password "sa"})))
    (println r)
    (is (some? r))
    (def id (first (vals r)))
    (is (< 0 id))
    (def saved (first (k/select data_source (k/where {:id id}))))
    (println saved)
    (is (some? saved))
    (is (some? (safe-mk-ds saved)))
    )  
  )

