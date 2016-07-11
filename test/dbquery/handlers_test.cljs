(ns dbquery.handlers-test
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]))

(deftest test-set-order
  (testing "set simple order"
    (is (= [[1 :up]] (set-order [] 1 next-order)))))

(cljs.test/report :end-run-tests)
(cljs.test/run-tests)
