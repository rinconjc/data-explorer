(ns dbquery.conf
  (:require [clojure.string :as str]))

(def db-conf  (let [db-env (System/getProperty "app-env" "test")
                    db (case db-env
                         "test" "mem:test-model"
                         (format "~/dbquery-%s-h2.db;AUTO_SERVER=TRUE" db-env))]
                {:db db
                 :user "sa"
                 :password "sa"
                 :delimiters ["" ""]
                 :naming {:keys str/lower-case :fields str/lower-case}
                 :version 1}))
