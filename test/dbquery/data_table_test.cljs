(ns dbquery.data-table-test
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]
            [dbquery.sql-utils :as su]))

;; (deftest test-funs
;;   (testing "roll-sort"
;;     (is (= [nil nil :up] (su/roll-sort [] 2)))))

(cljs.test/report :end-run-tests)
(cljs.test/run-tests)
