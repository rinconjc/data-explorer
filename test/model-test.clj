(ns dbquery.model-test
  (:require [clojure.test :refer :all]
            [dbquery.model :refer :all]))

(sync-db 2 "test")

(deftest test-model
  (testing "create datasource"
    (is (some? 1))))
