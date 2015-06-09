(ns dbquery.core-test
  (:require [clojure.test :refer :all]
            [dbquery.core :refer :all]
            [dbquery.dbfixture :refer :all]))

(use-fixtures :each fixture model-fixture)

(deftest handlers
  (testing "handle exec query"
    (def res (handle-exec-query {:body {:tables ["TABLEA"] :fields ["*"]}} 1))
    (is (some? (:body res)))
    ))
