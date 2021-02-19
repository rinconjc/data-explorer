(ns dbquery.conf
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import java.io.File
           java.lang.Exception))

(defn- rand-str [len]
  (->> (repeatedly len #(rand-int 61))
       (map #(cond (< % 10) %
                   (<= % 35) (char (+ % 55))
                   :else (char (+ % 61))))
       (str/join)))

(defn default-conf []
  (let [home-dir (str (System/getProperty "user.home") "/.dbexplorer")
        conf-file (File. home-dir "conf.edn")]
    (when-not (.exists conf-file)
      (. (File. home-dir) mkdir)
      (spit conf-file (pr-str {:app-env "prod"
                               :db {:db (str home-dir "/app-db.db")
                                    :user "admin"
                                    :password (rand-str 20)}
                               :secret-key (rand-str 40)})))
    conf-file))

(defn- load-conf
  ([file]
   (log/info "loading conf from " file)
   (with-open [reader  (java.io.PushbackReader. (io/reader file))]
     (edn/read reader)))
  ([]
   (update
    (if-let [conf-file (or (System/getProperty "conf"))]
     (load-conf (java.io.File. conf-file))
     (load-conf (default-conf)))
    :index-dir #(or % (str (System/getProperty "user.home") "/index")))))

(def conf (delay (load-conf)))

(def db-conf  (delay (merge (@conf :db) {:delimiters ["" ""]
                                  :naming {:keys str/lower-case :fields str/lower-case}
                                  :version 1})))
