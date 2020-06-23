(ns dbquery.handlers-test
  (:require [cljs.test :refer-macros [deftest is run-tests testing]]
            [dbquery.sql-utils :as su ]))

(deftest test-set-order
  (testing "set simple order"
    (is (= [[1 :up]] (su/set-order [] 1 su/next-order)))))

(cljs.test/report :end-run-tests)
(cljs.test/run-tests)
