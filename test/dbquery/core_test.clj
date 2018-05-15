(ns dbquery.core-test
  (:require [clojure.string :as str]
            [clojure.test :refer :all]
            [dbquery.core :refer :all]
            [dbquery.dbfixture :refer :all]))

(use-fixtures :each fixture model-fixture)

(deftest handlers
  (testing "handle exec query"
    (def res (handle-exec-query {:body {:tables ["TABLEA"] :fields ["*"]}} 1))
    (is (some? (:body res))))

  (testing "handle table list"
    (def res (handle-list-tables {} 1))
    (println "result:" res)
    (is (coll? res))))

(deftest test-resources
  (testing "query-list"
    (def res (queries-list {})))
  (is (= (str "a") (str/trim "  a"))))
