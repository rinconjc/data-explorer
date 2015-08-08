(ns dbquery.databases-test
  (:require [clojure.test :refer :all]
            [dbquery.databases :refer :all]
            [dbquery.dbfixture :refer :all]
            [dbquery.databases :refer :all])
  (:import [java.sql ResultSet Types])
  )

(use-fixtures :each fixture)

(deftest test-database
  (testing "make datasource"
    (is (some? (:datasource (safe-mk-ds {:dbms "H2" :url "mem:test;DB_CLOSE_DELAY=0"
                                         :user_name "sa" :password "sa"})))))
  (testing "get tables"
    (let [ds (dummy-ds)]
      (execute ds "create table tablex(id int, name varchar(10))")
      (def result (tables ds))
      (println "tables:" result)
      (is (= 1 (count result)))
      )
    )

  (testing "query tables"
    (def ds (dummy-ds))
    (execute ds ["create table TEST (id int)"
                 "insert into test values(1)"
                 "insert into test values(2)"])
    (is (= 2 (count (:rows (table-data ds "test")))))
    )
  (testing "exec-query"
    (def ds (dummy-ds))
    (execute ds "create table test1(id int, desc varchar(40), created date, primary key(id))")
    (execute ds
             (for [i (range 50)] (format "insert into test1 values (%1$d, 'desc of %1$d', sysdate)" i)))
    (def data (exec-query ds {:tables ["test1"] :fields ["id" "desc" "created"] :offset 2 :limit 50}))
    (is (some? (:columns data)))
    (is (= 48 (count (:rows data))))
    )

  (testing "table-meta"
    (def ds (dummy-ds))
    (execute ds ["create table tablea(id int, desc varchar(40), primary key(id))"
                 "create table tableb(id int, aid int, desc varchar(20), primary key (id), foreign key(aid) references tablea(id) )"])
    (let [{cols :columns pks :primaryKeys fks :foreignKeys} (table-meta ds "TABLEB")]
      (println "cols: " (pr-str cols))
      (println "pks: " (pr-str pks))
      (println "fks: " (pr-str fks))
      (is (= 3 (count cols)))
      (is (= 1 (count pks)))
      (is (= 1 (count fks))))
    )

  (testing "exec raw sql - query"
    (is (vector? (:rows (execute (dummy-ds) "SELECT 1 FROM DUAL")))))

  (testing "accessing clob data"
    (let [ds (dummy-ds)
          text "a very large chunk of text...blah blah"
          _ (execute ds ["create table texttable(id int, data text)"
                         (format "insert into texttable values(1, '%s')" text)])
          data (with-open [con (.getConnection (:datasource ds))]
                 (-> con .createStatement (.executeQuery "select * from texttable") read-as-map))]
      (println "data:" data)
      (is (= text (:data (first data)))))
    )

  (testing "data load"
    (let [ds (dummy-ds)
          _ (execute ds "create table loadtable(id int, name varchar(30))")
          ]      
      (is (= {:importCount 2 :invalidCount 1} (load-data ds "loadtable" {:header ["id" "name"] :rows [[1 "first"] [2 "second"] ["aa" "third"]]}
                             {:id {:source "id" :type Types/INTEGER}
                              :name {:source "name" :type Types/VARCHAR}})))
      (def rows (:rows  (execute ds "select * from loadtable")))
      (is (= 2 (count rows)))
      )
    )
  
  )
