(ns dbquery.conf
  (:require [clojure.string :as str]))

(def db-conf  (let [db-env (System/getProperty "app-env" "test")
                    db (case db-env
                         "test" "mem:model;DB_CLOSE_DELAY=-1"
                         (format "~/dbquery-%s-h2.db" db-env))]
                {:db db
                 :user "sa"
                 :password "sa"
                 :delimiters ["" ""]
                 :naming {:keys str/lower-case :fields str/lower-case}
                 :version 1}))
