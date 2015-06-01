(ns dbquery.databases-test
  (:require [clojure.test :refer :all]
            [dbquery.databases :refer :all]))

(defn dummy-ds [] (safe-mk-ds {:dbms "H2" :url  "mem:test;DB_CLOSE_DELAY=0" :user_name  "sa" :password "sa"}))
(defn fixture [f]
  (def con (.getConnection (:datasource (dummy-ds))))
  (f)
  (.close con)
  )
(use-fixtures :each fixture)

(deftest test-database
  (testing "make datasource"
    (is (some? (:datasource (safe-mk-ds {:dbms "H2" :url "mem:test;DB_CLOSE_DELAY=0"
                                         :user_name "sa" :password "sa"})))))
  (testing "get tables"
    (is (= 0 (count (tables (dummy-ds))))))

  (testing "exec-query"
    (def ds (dummy-ds))
    (apply (partial execute ds) "create table test(id int, desc varchar(40), created date, primary key(id))"
           (for [i (range 50)] (format "insert into test values (%1$d, 'desc of %1$d', sysdate)" i)))
    (def data (exec-query ds :tables ["test"] :fields ["id" "desc" "created"] :offset 2))
    (is (some? (:columns data)))
    (is (= 48 (count (:rows data))))
    )
  (testing "table-meta"
    (def ds (dummy-ds))
    (execute ds "create table tablea(id int, desc varchar(40), primary key(id))"
             "create table tableb(id int, aid int, desc varchar(20), primary key (id), foreign key(aid) references tablea(id) )")
    (let [{cols :columns pks :primary-keys fks :foreign-keys} (table-meta ds "TABLEB")]
      (println "cols: " (pr-str cols))
      (println "pks: " (pr-str pks))
      (println "fks: " (pr-str fks))
      (is (= 3 (count (:rows cols))))
      (is (= 1 (count (:rows pks))))
      (is (= 1 (count (:rows fks)))))
   )
  )
