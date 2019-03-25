(ns dbquery.sql-utils-test
  (:require [clojure.test :refer [deftest testing is]]
            [dbquery.sql-utils :refer [sql-statements set-order next-order]]))

(deftest parse-sql-stmts
  (testing "simple parse"
    (is (= ["select x from y"] (sql-statements "select x from y")))
    (is (= ["select x from y" "create table t(col1 int --inline comment\n)"]
           (sql-statements "select x from y;\ncreate table t(col1 int --inline comment\n);"))))

  (testing "parse pl/sql blocks"
    (is (= ["select x from y" "begin\nstmt1;\nstmt2;\n end\n;"]
           (sql-statements "select x from y/\nbegin\nstmt1;\nstmt2;\n end\n;/")))))

(deftest sql-ops
  (testing "set order"
    (is (= [[1 :up]] (set-order [] 1 next-order)))
    (is (= [[1 :down]] (set-order [[1 :up]] 1 next-order)))
    (is (= [] (set-order [[1 :down]] 1 next-order)))))
