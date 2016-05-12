(ns dbquery.sql-utils-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [dbquery.sql-utils :refer :all]))

(deftest parse-sql-stmts
  (testing "simple parse"
    (is (= ["select x from y"] (sql-statements "--comment \nselect x from y")))
    (is (= ["select x from y"] (sql-statements "--comment \n/* blah blah
more comments*/select x from y;--final comments")))
    (is (= ["select x from y" "create table t(col1 int --inline comment\n)"]
           (sql-statements "--comment \n/* blah blah
more comments*/select x from y;\ncreate table t(col1 int --inline comment\n);")))))
