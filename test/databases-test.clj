(ns dbquery.databases-test
  (:require [clojure.test :refer :all]
            [dbquery.databases :refer :all]))

(defn dummy-ds [] (mk-ds {:dbms "H2" :url "mem:test;DB_CLOSE_DELAY=-1" :user_name "sa" :password "sa"}))

(deftest test-database
  (testing "make datasource"
    (is (some? (:datasource (mk-ds {:dbms "H2" :url "mem:" :user_name "sa" :password "sa"})))))

  (testing "get tables"
    (is (some? (pr-str (tables (dummy-ds))))))

  (testing "query tables"
    (def ds (dummy-ds))
    (execute ds "create table TEST (id int)"
             "insert into test values(1)"
             "insert into test values(2)")
    (is (= 2 (count (:rows (table-data ds "test")))))
    )

  (testing "exec raw sql - query"
    (is (vector? (:rows (execute (dummy-ds) "SELECT 1 FROM DUAL"))))))
