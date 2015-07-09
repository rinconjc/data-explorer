(ns dbquery.databases
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :refer :all]
            [clojure.string :as s]
            [dbquery.utils :refer :all]
            )
  (:import [org.h2.jdbcx JdbcDataSource]
           [oracle.jdbc.pool OracleDataSource]
           [java.sql ResultSet Types])
  )

(def ^:private result-extractors
  {
   Types/BIT (fn [rs i] (.getBoolean rs i))
   Types/TIMESTAMP (fn [rs i] (.getTimestamp rs i))
   Types/CLOB (fn [rs i] (->(.getClob rs i) .getCharacterStream slurp))})

(defn- col-reader [sql-type]
  (get result-extractors sql-type (fn [rs i] (.getObject rs i)))
  )

(defn ^:private rs-rows [rs row-reader offset limit]
  (if (and (> offset 0) (= ResultSet/TYPE_SCROLL_INSENSITIVE (.getType rs)))
    (.absolute rs offset)
    )
  (loop [rows [] count 0]
    (if (and (.next rs) (< count limit))
      (let [row (doall (apply row-reader [rs]))]
        (recur (conj rows row) (inc count))
        )
      rows
      )
    )
  )

(defn ^:private read-rs
  [rs & {:keys [offset limit columns] :or {offset 0 limit 20}}]
  (let [rs-meta (.getMetaData rs)
        col-count (inc (.getColumnCount rs-meta))
        cols (doall (for [i (range 1 col-count) :let [col-name (.getColumnLabel rs-meta i)] :when (or (nil? columns) (some #{col-name} columns))]
                      [i col-name (col-reader (.getColumnType rs-meta i))]))
        row-reader (fn [rs] (for [[i _ reader] cols] (apply reader [rs i])))
        ]
    {:columns (map second cols) :rows (doall (rs-rows rs row-reader offset limit))}
    )
  )

(defn read-as-map [rs & {:keys [offset limit] :or {offset 0 limit 100}}]
  (let [meta (.getMetaData rs)
        col-count (inc (.getColumnCount meta))
        col-and-readers (doall (for [i (range 1 col-count)] [i (keyword (s/lower-case (.getColumnLabel meta i))) (col-reader (.getColumnType meta i))]))
        row-reader (fn [rs] (reduce (fn [row [i col reader]] (assoc row col (apply reader [rs i]))) {} col-and-readers))]
    (rs-rows rs row-reader offset limit)
    ))

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
  (with-recovery {:datasource (mk-ds ds-info) :schema (:schema ds-info)}
    (fn [e] {:error (.getMessage e)})
    )
  )

(defn table-data
  ([ds table limit]
   (db-query-with-resultset ds
                            [(str "SELECT * FROM " table)]
                            #(read-rs % :limit limit))
   )
  ([ds table] (table-data ds table 100)))

(defn tables
  ([ds] (tables ds (:schema ds)))
  ([ds schema]
   (with-db-metadata [meta ds]
     (with-open [rs (.getTables meta nil schema "%" (into-array ["TABLE" "VIEW"]))]
       (-> (read-rs rs :columns ["TABLE_NAME"] :limit 1000)
           :rows
           flatten)))))

(defn execute [ds sql & opts]
  (with-open [con (.getConnection (:datasource ds))]
    (let [sqlv (if (vector? sql) sql [sql])]
      (loop [sql (first sqlv)
             sqls (rest sqlv)]
        (let [stmt (.createStatement con ResultSet/TYPE_SCROLL_INSENSITIVE ResultSet/CONCUR_READ_ONLY)
              has-rs (.execute stmt sql)]
          (if (empty? sqls)
            (if has-rs
              (read-rs (.getResultSet stmt) opts)
              (.getUpdateCount stmt)
              )
            (recur (first sqls) (rest sqls)))
          )))
    )
  )

(defn table-meta [ds name]
  (with-db-metadata [meta ds]
    (let [cols  (with-open [rs (.getColumns meta nil nil name "%")]
                  (read-rs rs :columns ["COLUMN_NAME" "DATA_TYPE" "TYPE_NAME" "COLUMN_SIZE" "DECIMAL_DIGITS" "NULLABLE"] :limit 200))
          pks (with-open [rs (.getPrimaryKeys meta nil nil name)]
                (read-rs rs :columns ["COLUMN_NAME" "KEY_SEQ" "PK_NAME"]))
          fks (with-open [rs (.getImportedKeys meta nil nil name)]
                (read-rs rs :columns ["PKTABLE_NAME" "PKCOLUMN_NAME" "FKCOLUMN_NAME" "KEY_SEQ" "FKTABLE_NAME"]))]
      {:columns cols :primaryKeys pks :foreignKeys fks})))

(defn exec-query [ds {:keys [tables fields predicates offset limit] :or {offset 0 limit 20}}]
  (with-open [con (.getConnection (:datasource ds))]
    (let [stmt (if (> offset 0)
                 (.createStatement con ResultSet/TYPE_SCROLL_INSENSITIVE ResultSet/CONCUR_READ_ONLY)
                 (.createStatement con))
          where (if (empty? predicates) "" (str " where " (s/join " AND " predicates)))
          sql (str "select " (s/join "," fields) " from " (s/join "," tables) where)
          rs (.executeQuery stmt sql)]
      (read-rs rs :limit limit :offset offset)
      )))
