(ns dbquery.databases
  (:require [clojure.data.csv :as csv]
            [clojure.java.jdbc :refer :all]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [dbquery.utils :refer :all])
  (:import com.zaxxer.hikari.HikariDataSource
           java.lang.StringBuilder
           [java.sql Connection PreparedStatement ResultSet Timestamp Types]
           [java.text DecimalFormat NumberFormat SimpleDateFormat]))

(def ^:private result-extractors
  {Types/BIT (fn [rs i] (.getBoolean rs i))
   Types/TIMESTAMP (fn [rs i] (.getTimestamp rs i))
   -101 (fn [rs i] (.getTimestamp rs i))
   Types/CLOB (fn [rs i] (some-> (.getClob rs i) .getCharacterStream slurp))})

(def ^:private sql-date-types #{Types/DATE Types/TIMESTAMP Types/TIME})
(def ^:private sql-number-types #{Types/NUMERIC Types/DECIMAL Types/INTEGER Types/DOUBLE})

(def ^:private executing-queries (atom {}))

(defn- col-reader [sql-type]
  (get result-extractors sql-type
       (fn [^ResultSet rs ^Integer i] (.getObject rs i))))

(defn ^:private rs-rows [rs row-reader offset limit]
  (if (and (> offset 0) (= ResultSet/TYPE_SCROLL_INSENSITIVE (.getType rs)))
    (.absolute rs offset))
  (loop [rows [] count 0]
    (if (and (.next rs) (< count limit))
      (let [row (doall (apply row-reader [rs]))]
        (recur (conj rows row) (inc count)))
      rows)))

(defn ^:private read-rs
  ([rs {:keys [offset limit columns] :or {offset 0 limit 20}}]
   (let [rs-meta (.getMetaData rs)
         col-count (inc (.getColumnCount rs-meta))
         cols (doall (for [i (range 1 col-count) :let [col-name (.getColumnLabel rs-meta i)] :when (or (nil? columns) (some #{col-name} columns))]
                       [i col-name (col-reader (.getColumnType rs-meta i))]))
         row-reader (fn [rs] (for [[i _ reader] cols] (apply reader [rs i])))]
     {:columns (map second cols) :rows (doall (rs-rows rs row-reader offset limit))}))
  ([rs] (read-rs rs {})))

(defn- key-map [xs]
  (and xs (into {} (for [e xs] (cond
                                 (string? e) [e (keyword (s/lower-case e))]
                                 (vector? e) [(first e) (second e)])))))

(defn read-as-map
  ([rs {:keys [offset limit fields] :or {offset 0 limit 100}}]
   (let [fields-map (key-map fields)
         meta (.getMetaData rs)
         col-count (-> (.getColumnCount meta) inc int)
         col-and-readers (doall (for [i (range (int 1) col-count)
                                      :let [col-name (.getColumnLabel meta i)
                                            key (or (and (nil? fields) (keyword (s/lower-case col-name)))
                                                    (fields-map col-name))]
                                      :when (some? key)]
                                  [i key (col-reader (.getColumnType meta i))]))
         row-reader (fn [rs] (reduce (fn [row [i col reader]]
                                       (assoc row col (apply reader [rs i]))) {} col-and-readers))]
     (rs-rows rs row-reader offset limit)))
  ([rs] (read-as-map rs {})))

(defn rs-to-csv [rs writer {:keys[limit] :or {limit 1000000}}]
  (let [meta (.getMetaData rs)
        col-indexes (range 1 (inc (.getColumnCount meta)))
        headers (for [i col-indexes] (.getColumnLabel meta i))
        readers (into {} (for [i col-indexes] [i (col-reader (.getColumnType meta i))]))]
    (csv/write-csv writer [headers])
    (loop [has-next (.next rs)
           count 1]
      (when has-next
        (->> (for [i col-indexes] (apply (readers i) [rs i]))
             (vector)
             (csv/write-csv writer))
        (when (= 0 (mod count 100 ))
          (.flush writer))
        (recur (.next rs) (inc count))))))

(defn mk-ds [{:keys [dbms url user_name password] :as params}]
  "Creates a datasource"
  (let [[driver jdbc-url]
        (case dbms
          "H2" ["org.h2.jdbcx.JdbcDataSource" (str "jdbc:h2:" url)]
          "ORACLE" ["oracle.jdbc.pool.OracleDataSource" (str "jdbc:oracle:thin:@" url)]
          "POSTGRES" ["org.postgresql.Driver" (str "jdbc:postgresql:" url)]
          "MS-SQL" ["net.sourceforge.jtds.jdbcx.JtdsDataSource" (str "jdbc:jtds:sqlserver://" url)]
          "Sybase" ["net.sourceforge.jtds.jdbcx.JtdsDataSource" (str "jdbc:jtds:sybase://" url)]
          "MySQL" ["com.mysql.cj.jdbc.Driver" (str "jdbc:mysql://" url)]
          "Presto" ["com.facebook.presto.jdbc.PrestoDriver" (str "jdbc:presto://" url)]
          (throw (Exception. (format "DBMS %s not supported." dbms))))
        ds (doto (HikariDataSource.)
             (.setJdbcUrl jdbc-url)
             (.setUsername user_name)
             (.setPassword password)
             (.setLoginTimeout 20000)
             (.setConnectionTimeout 5000)
             (.setMinimumIdle 0)
             (.setMaximumPoolSize 4)
             (.setIdleTimeout 180000)
             (.setMaxLifetime 300000))]
    (case  dbms
      "MS-SQL" (.setConnectionTestQuery ds "SELECT GETDATE()")
      "Sybase" (do (.setConnectionTestQuery ds "SELECT GETDATE()")
                   (when (not-empty (:schema params))
                     (.setConnectionInitSql ds (str "USE " (:schema params)))))
      "ORACLE" (.setConnectionInitSql
                ds (str "ALTER SESSION SET CURRENT_SCHEMA=" (or (:schema params) (:user_name params))))
      nil)
    (with-open [con (.getConnection ds)]
      ds)))

(defn safe-mk-ds [ds-info]
  "Creates a datasource"
  (with-recovery {:datasource (mk-ds ds-info) :schema (:schema ds-info)}
    (fn [e] {:error (.getMessage e)})))

(defn table-data
  "retrieves table data"
  ([ds table limit]
   (db-query-with-resultset ds [(str "SELECT * FROM " table)] #(read-rs % {:limit limit})))
  ([ds table] (table-data ds table 100)))

(defn- get-db-tables [meta schema]
  (with-open [rs (.getTables meta nil (not-empty schema) "%"
                             (into-array ["TABLE" "VIEW"]))]
    (read-as-map rs {:fields [["TABLE_NAME" :name] ["TABLE_TYPE" :type]] :limit 1000})))

(defn get-tables
  "retrieves the tables in the given or (current) schema"
  ([ds] (get-tables ds (:schema ds)))
  ([ds schema]
   (with-db-metadata [meta ds]
     (get-db-tables meta schema))))

(defn data-types [ds]
  "retrieves the data types supported by the datasource"
  (with-db-metadata [meta ds]
    (with-open [rs (.getTypeInfo meta)]
      (read-as-map rs {:fields ["TYPE_NAME", "DATA_TYPE"]}))))

(defmulti fetch-db-output first)
(defmethod fetch-db-output :default [_] nil)
(defmethod fetch-db-output "ORACLE" [[_ ^Connection con]]
  (try
    (let [buffer (StringBuilder.)
          stmt (doto (.prepareCall con "
declare
line varchar2(255);
done number;
buffer long;
begin
        loop
                exit when length(buffer)+255>:maxbytes OR done=1;
                dbms_output.get_line(line, done);
                if line is not null then
                        buffer:=buffer||line||chr(10);
                end if;
        end loop;
:done:=done;
:buffer:=buffer;
end;
")
                 (.registerOutParameter 2 Types/INTEGER)
                 (.registerOutParameter 3 Types/VARCHAR))]
      (loop []
        (doto stmt (.setInt 1 32000) (.executeUpdate))
        (some->> (.getString stmt 3) (.append buffer))
        (when-not (= 1 (.getInt stmt 2))
          (recur)))
      (str buffer))
    (catch Exception e
      (log/error e "failed retrieving db output"))))

(defn find-last-result [stmt reader opts]
  (loop []
    (let [rs (or (some-> (.getResultSet stmt)
                         (reader opts)
                         (doall)
                         (#(hash-map :data %)))
                 {:rowsAffected (.getUpdateCount stmt)} )]
      (if (or (.getMoreResults stmt) (not (neg? (.getUpdateCount stmt)))) (recur) rs))))

(defn execute
  "executes the given sql statement returning the resulting rows or the number
  of rows affected by the statement"
  ([{:keys [datasource dbms] :as ds} sql {:keys [rs-reader args id] :or {rs-reader read-rs} :as opts}]
   (with-open [con (.getConnection datasource)]
     (let [sqlv (if (coll? sql) sql [sql])]
       (loop [sql (first sqlv)
              sqls (rest sqlv)]
         (let [^PreparedStatement stmt (.prepareStatement con sql ResultSet/TYPE_FORWARD_ONLY
                                                          ResultSet/CONCUR_READ_ONLY)
               _ (if (some? args) (reduce #(do (.setObject stmt %1 %2)
                                               (inc %1)) 1 args))
               _ (when id (swap! executing-queries assoc id stmt))
               _ (try
                   (.execute stmt)
                   (catch Exception e
                     (throw (ex-info (.getMessage e)
                                     {:output (fetch-db-output [dbms con])} e)))
                   (finally
                     (when id (swap! executing-queries dissoc id))))]
           (if (empty? sqls)
             (assoc (find-last-result stmt rs-reader opts)
                    :output (fetch-db-output [dbms con]))
             (recur (first sqls) (rest sqls))))))))
  ([ds sql] (execute ds sql {})))

(defn cancel-query [query-id]
  (log/info "stmts:" @executing-queries)
  (when-let [stmt (.unwrap (@executing-queries query-id) java.sql.Statement)]
    (try
      (log/infof "cancelling stmt %s" stmt)
      (log/spyf :info "cancelled: %s" (.cancel stmt))
      (catch Exception e
        (log/error e "failed cancelling query " query-id)))))

(defn- table-columns [meta schema table]
  (with-open [rs (.getColumns meta nil schema table "%")]
    (read-as-map rs {:fields ["TABLE_NAME" ["COLUMN_NAME" :name]
                              "DATA_TYPE" "TYPE_NAME"
                              ["COLUMN_SIZE" :size] "NULLABLE"] :limit Integer/MAX_VALUE})))

(defn- table-pks [meta schema table]
  (with-open [rs (.getPrimaryKeys meta nil schema table)]
    (read-as-map rs {:fields [["COLUMN_NAME" :name] "KEY_SEQ"]})))

(defn- table-fks [meta schema table]
  (with-open [rs (.getImportedKeys meta nil schema table)]
    (read-as-map rs {:fields ["PKTABLE_NAME" "PKCOLUMN_NAME"
                              "FKCOLUMN_NAME" "KEY_SEQ"
                              "FK_NAME" "PK_NAME"]})))

(defn- merge-col-keys [cols pks fks]
  (map (fn [{col-name :name :as col}]
         (-> col
             (assoc :is_pk (some? (some #(= col-name (:name %)) pks)))
             ((fn [m]
                (if-let [fk (some #(if (= col-name (:fkcolumn_name %)) %) fks)]
                  (assoc m :is_fk true :fk_table (:pktable_name fk) :fk_column (:pkcolumn_name fk))
                  (assoc m :is_fk false)))))) cols))

(defn table-cols [ds name]
  "retrieves the columns of the given table"
  (log/info "table-cols for dbms" (:dbms ds))
  (let [schema (when-not (= "Sybase" (:dbms ds)))]
    (with-db-metadata [meta ds]
      (let [cols  (future (table-columns meta schema name))
            pks (future (table-pks meta schema name))
            fks (future (table-fks meta schema name))]
        (merge-col-keys @cols @pks @fks)))))

(defn db-meta [ds]
  "retrieves all the tables and columns in the current schema"
  (with-db-metadata [meta ds]
    (let [tables (future (get-db-tables meta (:schema ds)))
          cols (future (group-by #(:table_name %) (table-columns meta (:schema ds) "%")))
          pks (into {} (for [tbl @tables :let [tbl-name (:name tbl)]]
                         [tbl-name (future (table-pks meta tbl-name))]))
          fks (into {} (for [tbl @tables :let [tbl-name (:name tbl)]]
                         [tbl-name (future (table-fks meta tbl-name))]))]
      (doall (for [tbl @tables :let [tbl-name (:name tbl)]]
               (assoc tbl :columns (merge-col-keys (@cols tbl-name)
                                                   (deref (pks tbl-name))
                                                   (deref (fks tbl-name)))))))))

(defn to-sql
  "converts a hash-map representation of a query into a SQL query
  :tables maybe a keyword, a tuple to represent table and alias,
  a hash-map {:join :left|:right :to 'joined table' :from 'joining table'
  :on :infer|'conditions'}, a string for subqueries"
  [{:keys [tables fields predicates offset limit order groups]
    :or {offset 0 limit 40}}]
  (let [where (if-not (empty? predicates)
                (str " where " (s/join " AND " predicates)))
        from (->> tables
                  (map #(cond
                          (vector? %) (s/join " " %)
                          (map? %) (str (:join %) "JOIN "))))
        order-by (if-not (empty? order)
                   (str " order by " (->> order
                                          (map #(cond
                                                  (string? %) %
                                                  (neg? %) (str (- %) " desc")
                                                  :else %))
                                          (s/join ",")
                                          (str " order by "))))
        group-by (if-not (empty? groups) (s/join "," groups))
        sql (str "select " (s/join "," fields) " from " (s/join "," tables)
                 where group-by order-by)]))

(defn exec-query [ds {:keys [tables fields predicates offset limit order group]
                      :or {offset 0 limit 40}}]
  (with-open [con (.getConnection (:datasource ds))]
    (let [stmt (if (> offset 0)
                 (.createStatement con ResultSet/TYPE_FORWARD_ONLY ResultSet/CONCUR_READ_ONLY)
                 (.createStatement con))
          where (if (empty? predicates) "" (str " where " (s/join " AND " predicates)))
          sql (str "select " (s/join "," fields) " from " (s/join "," tables) where)
          rs (.executeQuery stmt sql)]
      (read-rs rs {:limit limit :offset offset}))))

(defn create-table [ds name cols pk]
  (let [col-defs (for [{:keys [column_name type_name size]} cols
                       :let [typedef (if (some? size) (str type_name "(" size ")") type_name)]]
                   (str column_name " " typedef " null"))
        pk-def (if (some? pk) (str ", PRIMARY KEY(" pk ")") "")]
    (execute ds (str "CREATE TABLE " name "(" (s/join "," col-defs) pk-def ")"))
    name))

(defn- value-converter [type format]
  (cond
    (sql-date-types type) (fn [val] (-> (SimpleDateFormat. format)
                                        (.parse val)
                                        .getTime
                                        (Timestamp.)))
    (sql-number-types type) (if (s/blank? format)
                              (fn [val] (-> (NumberFormat/getNumberInstance) (.parse val)))
                              (fn [val] (-> (DecimalFormat. format) (.parse val))))
    true identity))

(defn load-data [ds table {header :header rows :rows} mappings]
  (defn param-setter [{:keys [source format type]} i]
    (let [pos (.indexOf header source)
          _ (if (< pos 0) (throw (Exception. (str "source " source " not found in header " (s/join "," header)))))
          val-fn (value-converter type format)]
      (fn [ps row]
        (let [val (nth row pos)]
          (try
            (if (s/blank? val)
              (doto ps (.setNull (inc i) type))
              (doto ps (.setObject (inc i) (val-fn val) type)))
            (catch Exception e
              (log/error e "failed converting col " pos " in " row)
              (throw e)))))))

  (let [valid-mappings (filter #(contains? (second %) :source)  mappings)
        cols (keys valid-mappings)
        row-mapper (for [col cols :let [{:keys [source format type]} (valid-mappings col)
                                        i (.indexOf header source)
                                        val-fn (value-converter type format)]]
                     (fn [row] (val-fn (nth row i))))
        param-setters (doall (into {} (for [[col mapping] valid-mappings]
                                        [col (param-setter mapping (.indexOf cols col))])))
        insert-sql (str "INSERT INTO " table "(" (s/join "," (map name cols)) ") VALUES("
                        (s/join "," (repeat (count cols) "?")) ")")]
    ;; (apply jdbc/insert! ds table cols (map row-mapper rows))
    (with-open [con (.getConnection (:datasource ds))]
      (let [ps (.prepareStatement con insert-sql)
            errors (doall (for [row rows]
                            (try-let [_ (reduce #((param-setters %2) %1 row) ps cols)]
                                     (.addBatch ps)
                                     (fn [e] (log/warn e "failed mapping row " row)
                                       [row e]))))
            rows-inserted (.executeBatch ps)]
        {:importCount (reduce + rows-inserted)
         :invalidCount (count (filter some? errors))}))))
