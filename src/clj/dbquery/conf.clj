(ns dbquery.conf
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.edn :as edn]))

(defn- load-conf
  ([file]
   (log/info "loading conf from " file)
   (with-open [reader  (java.io.PushbackReader. (io/reader file))]
     (edn/read reader)))
  ([]
   (if-let [conf-file (System/getProperty "conf")]
     (load-conf (java.io.File. conf-file))
     (load-conf (io/resource "sample-conf.edn"))
     )
   )
  )

(def conf (load-conf))

(def db-conf  (merge (conf :db) {
                                 :delimiters ["" ""]
                                 :naming {:keys str/lower-case :fields str/lower-case}
                                 :version 1}))
