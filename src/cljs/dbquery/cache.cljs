(ns dbquery.cache
  (:require [clojure.edn :refer [read-string]]
            [oops.core :as o]
            [clojure.string :as str]))

(defn- as-str [x]
  (if (vector? x) (str/join "/" x) x))

(defn put-entry! [path value]
  (o/ocall js/localStorage "setItem" (as-str path) (pr-str value)))

(defn get-entry [path]
  (some-> (o/ocall js/localStorage "getItem" (as-str path))
          (read-string)))
