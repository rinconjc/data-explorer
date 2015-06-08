(ns dbquery.databases
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :refer :all]
            [clojure.string :as s])
  (:import [org.h2.jdbcx JdbcDataSource]
           [oracle.jdbc.pool OracleDataSource]
           [java.sql ResultSet])
  )


(defmacro try-let [binding then elsefn]
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

(defn ^:private rs-rows [rs cols offset limit]
  (if (and (> offset 0) (= ResultSet/TYPE_SCROLL_INSENSITIVE (.getType rs)))
    (.absolute rs offset)
    )
  (loop [rows [] count 0]
    (if (and (.next rs) (< count limit))
      (let [row (doall (map #(.getObject rs %) cols))]
        (recur (conj rows row) (inc count))
        )
      rows
      )
    )
  )

(defn ^:private read-rs
  [rs & {:keys [offset limit columns] :or {offset 0 limit 100}}]
  (if (some? columns)
    {:columns columns :rows (rs-rows rs columns offset limit)}
    (let [rs-meta (.getMetaData rs)
          col-size (inc (.getColumnCount rs-meta))
          cols (doall (map #(.getColumnName rs-meta %) (range 1 col-size)))]
      {:columns cols :rows (rs-rows rs cols offset limit)}
      )
    )
  )

(defn mk-ds [{:keys [dbms url user_name password]}]
  "Creates a datasource"
  (let [ds (case dbms
             "H2" (doto (JdbcDataSource.)
                    (.setUrl (str "jdbc:h2:" url))
                    (.setUser user_name)
                    (.setPassword password))
             "ORACLE" (doto (OracleDataSource.)
                        (.setURL (str "jdbc:oracle:thin:@" url))
                        (.setUser user_name)
                        (.setPassword password))
             (throw (Exception. (format "DBMS %s not supported." dbms))))]
    (with-open [con (.getConnection ds)]
      ds)
    )
  )

(defn safe-mk-ds [ds-info]
  "Creates a datasource"
  (with-recovery {:datasource (mk-ds ds-info)}
    #({:error (.getMessage %)})
    )
  )

(defn table-data
  ([ds table limit]
   (db-query-with-resultset ds
                            [(str "SELECT * FROM " table)]
                            #(read-rs % :limit limit))
   )
  ([ds table] (table-data ds table 100)))

(defn tables [ds]
  (with-db-metadata [meta ds]
    (with-open [rs (.getTables meta nil nil "%" (into-array ["TABLE" "VIEW"]))]
      (-> (read-rs rs :columns ["TABLE_NAME"])
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

(defn table-meta [ds name]
  (with-db-metadata [meta ds]
    (let [cols  (with-open [rs (.getColumns meta nil nil name "%")]
                  (read-rs rs :columns ["COLUMN_NAME" "DATA_TYPE" "TYPE_NAME" "COLUMN_SIZE" "DECIMAL_DIGITS" "NULLABLE" "ORDINAL_POSITION"] :limit 200))
          pks (with-open [rs (.getPrimaryKeys meta nil nil name)]
                (read-rs rs :columns ["COLUMN_NAME" "KEY_SEQ" "PK_NAME"]))
          fks (with-open [rs (.getImportedKeys meta nil nil name)]
                (read-rs rs :columns ["PKTABLE_NAME" "PKCOLUMN_NAME" "FKCOLUMN_NAME" "KEY_SEQ" "FKTABLE_NAME"]))]
      {:columns cols :primaryKeys pks :foreignKeys fks})))

(defn exec-query [ds &{:keys [tables fields predicates offset limit] :or {offset 0 limit 100}}]
  (with-open [con (.getConnection (:datasource ds))]
    (let [stmt (if (> offset 0)
                 (.createStatement con ResultSet/TYPE_SCROLL_INSENSITIVE ResultSet/CONCUR_READ_ONLY)
                 (.createStatement con))
          where (if (empty? predicates) "" (str " where " (s/join " AND " predicates)))
          sql (str "select " (s/join "," fields) " from " (s/join "," tables) where)
          rs (.executeQuery stmt sql)]
      (read-rs rs :limit limit :offset offset)
      )))
