(ns dbquery.databases
  (:require [clojure.tools.logging :as log]
            [clojure.java.jdbc :refer :all]
            [clojure.string :as s]
            [dbquery.utils :refer :all]
            )
  (:import [org.h2.jdbcx JdbcDataSource]
           [oracle.jdbc.pool OracleDataSource]
           [java.sql ResultSet Types]
           [java.text SimpleDateFormat DecimalFormat NumberFormat])
  )

(def ^:private result-extractors
  {
   Types/BIT (fn [rs i] (.getBoolean rs i))
   Types/TIMESTAMP (fn [rs i] (.getTimestamp rs i))
   Types/CLOB (fn [rs i] (->(.getClob rs i) .getCharacterStream slurp))})

(def ^:private sql-date-types #{Types/DATE Types/TIMESTAMP Types/TIME})
(def ^:private sql-number-types #{Types/NUMERIC Types/DECIMAL Types/INTEGER Types/DOUBLE})

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
  ([rs {:keys [offset limit columns] :or {offset 0 limit 20}}]
   (let [rs-meta (.getMetaData rs)
         col-count (inc (.getColumnCount rs-meta))
         cols (doall (for [i (range 1 col-count) :let [col-name (.getColumnLabel rs-meta i)] :when (or (nil? columns) (some #{col-name} columns))]
                       [i col-name (col-reader (.getColumnType rs-meta i))]))
         row-reader (fn [rs] (for [[i _ reader] cols] (apply reader [rs i])))
         ]
     {:columns (map second cols) :rows (doall (rs-rows rs row-reader offset limit))}
     ))
  ([rs] (read-rs rs {}))
  )

(defn read-as-map
  ([rs {:keys [offset limit fields] :or {offset 0 limit 100}}]
   (let [meta (.getMetaData rs)
         col-count (inc (.getColumnCount meta))
         col-and-readers (doall (for [i (range 1 col-count)
                                      :let [col-name (.getColumnLabel meta i)]
                                      :when (or (nil? fields) (some #{col-name} fields))]
                                  [i (keyword (s/lower-case col-name)) (col-reader (.getColumnType meta i))]))
         row-reader (fn [rs] (reduce (fn [row [i col reader]]
                                       (assoc row col (apply reader [rs i]))) {} col-and-readers))]
     (rs-rows rs row-reader offset limit)
     ))
  ([rs] (read-as-map rs {})))

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
                            #(read-rs % {:limit limit}))
   )
  ([ds table] (table-data ds table 100)))

(defn tables
  ([ds] (tables ds (:schema ds)))
  ([ds schema]
   (with-db-metadata [meta ds]
     (with-open [rs (.getTables meta nil schema "%" (into-array ["TABLE" "VIEW"]))]
       (-> (read-rs rs {:columns ["TABLE_NAME"] :limit 1000})
           :rows
           flatten)))))

(defn data-types [ds]
  (with-db-metadata [meta ds]
    (with-open [rs (.getTypeInfo meta)]
      (read-as-map rs :fields ["TYPE_NAME", "DATA_TYPE"])))
  )

(defn execute
  ([ds sql {:keys [rs-reader args] :or {rs-reader read-rs} :as opts}]
   (with-open [con (.getConnection (:datasource ds))]
     (let [sqlv (if (coll? sql) sql [sql])]
       (loop [sql (first sqlv)
              sqls (rest sqlv)]
         (let [stmt (.prepareStatement con sql ResultSet/TYPE_SCROLL_INSENSITIVE
                                       ResultSet/CONCUR_READ_ONLY)
               _ (if (some? args) (reduce #(do (.setObject stmt %1 %2)
                                               (inc %1)) 1 args))
               has-rs (.execute stmt)]
           (if (empty? sqls)
             (if has-rs
               (rs-reader (.getResultSet stmt) opts)
               (.getUpdateCount stmt)
               )
             (recur (first sqls) (rest sqls)))
           )))
     ))
  ([ds sql] (execute ds sql {}))
  )

(defn table-meta [ds name]
  (with-db-metadata [meta ds]
    (let [cols  (with-open [rs (.getColumns meta nil nil name "%")]
                  (read-as-map rs))
          pks (with-open [rs (.getPrimaryKeys meta nil nil name)]
                (read-as-map rs))
          fks (with-open [rs (.getImportedKeys meta nil nil name)]
                (read-as-map rs))]
      {:columns cols :primaryKeys pks :foreignKeys fks})))

(defn exec-query [ds {:keys [tables fields predicates offset limit] :or {offset 0 limit 20}}]
  (with-open [con (.getConnection (:datasource ds))]
    (let [stmt (if (> offset 0)
                 (.createStatement con ResultSet/TYPE_SCROLL_INSENSITIVE ResultSet/CONCUR_READ_ONLY)
                 (.createStatement con))
          where (if (empty? predicates) "" (str " where " (s/join " AND " predicates)))
          sql (str "select " (s/join "," fields) " from " (s/join "," tables) where)
          rs (.executeQuery stmt sql)]
      (read-rs rs {:limit limit :offset offset})
      )))

(defn create-table [ds name cols pk]
  (let [col-defs (for [{name :column_name {type :type_name} :type size :size} cols
                       :let [typedef (if (some? size) (str type "(" size ")") type)]]
                   (str name " " typedef))
        pk-def (if (some? pk) (str ", PRIMARY KEY(" pk ")") "")]
    (execute ds (str "CREATE TABLE " name "(" (s/join "," col-defs) pk-def ")"))
    name)
  )

(defn load-data [ds table {header :header rows :rows} mappings]
  (defn param-setter [{source :source format :format type :type} i]
    (let [pos (.indexOf header source)
          _ (if (< pos 0) (throw (Exception. (str "source " source " not found in header " (s/join "," header)))))
          val-fn (cond
                   (sql-date-types type) (fn [val] (-> (SimpleDateFormat. format) (.parse val)))
                   (sql-number-types type) (if (s/blank? format)
                                             (fn [val] (-> (NumberFormat/getNumberInstance) (.parse val)))
                                             (fn [val] (-> (DecimalFormat. format) (.parse val))))
                   true identity
                   )]
      (fn [ps row] (let [val (nth row pos)]
                     (if (s/blank? val)
                       (doto ps (.setNull (inc i) type))
                       (doto ps (.setObject (inc i) (val-fn val) type)))
                     )
        )
      )
    )
  (let [valid-mappings (filter #(contains? (second %) :source)  mappings)
        cols (keys valid-mappings)
        param-setters (doall (into {} (for [[col mapping] valid-mappings]
                                        [col (param-setter mapping (.indexOf cols col))])))
        insert-sql (str "INSERT INTO " table "(" (s/join "," (map name cols)) ") VALUES("
                        (s/join "," (repeat (count cols) "?" ) ) ")")]
    (with-open [con (.getConnection (:datasource ds))]
      (let [ps (.prepareStatement con insert-sql)
            errors (doall (for [row rows] (try-let [_ (reduce #((param-setters %2) %1 row) ps cols)]
                                                   (.addBatch ps)
                                                   (fn [e] (log/warn e "failed mapping row " row)
                                                     [row e]))))
            rows-inserted (.executeBatch ps)]
        {:importCount (reduce + rows-inserted) :invalidCount (count (filter some? errors))}
        )
      )
    )
  )
