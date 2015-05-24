(ns dbquery.databases-test
  (:require [clojure.test :refer :all]
            [dbquery.databases :refer :all]))

(defn dummy-ds [] (mk-ds "H2" "mem:" "sa" "sa"))

(deftest test-database
  (testing "make datasource"
    (is (some? (:datasource (mk-ds "H2" "mem:test;DB_CLOSE_DELAY=-1" "sa" "sa")))))
  
  (testing "get tables"
    (is (= "" (pr-str (tables (dummy-ds))))))
  )
