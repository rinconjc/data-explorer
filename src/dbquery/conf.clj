(ns dbquery.conf
  (:require [clojure.string :as str]))

(def db-conf  {:db "~/dbquery-h2.db"
               :user "sa"
               :password ""
               :naming {:keys str/lower-case :fields str/lower-case}
               :version 1})

