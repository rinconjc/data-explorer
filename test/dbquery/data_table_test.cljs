(ns dbquery.data-table-test
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]))

(deftest test-funs
  (testing "roll-sort"
    (is (= [nil nil :up] (roll-sort [] 2)))))

(cljs.test/report :end-run-tests)
(cljs.test/run-tests)
