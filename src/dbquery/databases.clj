(ns dbquery.databases
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :refer :all])
  (:import [org.h2.jdbcx JdbcDataSource])
  )


(defmacro try-let
  [binding then elsefn]
  `(try
     (let [~(first binding)  ~(second binding)]
       ~then
       )
     (catch Exception ex#
       (~elsefn ex#)
       )))
  

(defmacro with-recovery
  "tries to eval body, it recovers by evaluating the alternative"
  [body f]
  `(try
     (let [r# ~body]
       (if (seq? r#)
         (doall r#)
         r#))
     (catch Exception e#
       (log/error e# "recovering.")
       (~f e#))))


(defmacro wrap-error [& body]
  `(try
     {:result ~@body}
     (catch Exception e#
       (log/error e# "Failed executing operation with datasource")
       {:error (.getMessage e#)}))
  )

(defn ^:private read-rs
  ([rs limit]
   (let [rs-meta (.getMetaData rs)
         col-size (inc (.getColumnCount rs-meta))
         cols (doall (map #(.getColumnName rs-meta %) (range 1 col-size)))]
     (loop [rows [] count 0]
       (if (and (.next rs) (< count limit))
         (let [row (doall (map #(.getObject rs %) (range 1 col-size)))]
           (recur (conj rows row) (inc count))
           )
         {:columns cols :rows rows}
         )
       )
     ))
  ([rs] (read-rs rs (Integer/MAX_VALUE)))
  ([rs cols limit]
   (loop [rows [] count 0]
     (if (and (.next rs) (< count limit))
       (let [row (doall (map #(.getObject rs %) cols))]
         (recur (conj rows row) (inc count))
         )
       {:columns cols :rows rows}
       )
     )
   )
  )

(defn mk-ds [{:keys [dbms url user_name password]}]
  "Creates a datasource"
  (if-let [ds (case dbms
                "H2" (doto (JdbcDataSource.)
                       (.setUrl (str "jdbc:h2:" url))
                       (.setUser user_name)
                       (.setPassword password))
                "ORACLE" (doto ()))]
    (try
      (def con (.getConnection ds))
      {:datasource ds}
      (catch Exception e
        (log/error e "failed connecting to db")
        {:error (.getMessage e)}
        )
      (finally (if con (.close con)))))
  )

(defn table-data
  ([ds table limit]
   (db-query-with-resultset ds
                            [(str "SELECT * FROM " table)]
                            #(read-rs % limit))
   )
  ([ds table] (table-data ds table 100)))

(defn tables [ds]
  (with-db-metadata [meta ds]
    (with-open [rs (.getTables meta nil nil "%" (into-array ["TABLE" "VIEW"]))]
      (-> (read-rs rs ["TABLE_NAME"] 100)
          (:rows)
          flatten))))

(defn execute [ds raw-sql & more-sql]
  (with-open [con (.getConnection (:datasource ds))]
    (loop [sql raw-sql sqls more-sql]
      (let [stmt (.createStatement con)
            has-rs (.execute stmt sql)]
        (if (empty? sqls)
          (if has-rs
            (read-rs (.getResultSet stmt))
            (.getUpdateCount stmt)
            )
          (recur (first sqls) (rest sqls)))
        ))
    )
  )


;; (try-let [r (map #(/ 4 %) [2 3 1 4 5 0])]
;;          (println "all good " r)
;;          #(str "failed with " %))
