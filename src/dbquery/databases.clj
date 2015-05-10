(ns dbquery.databases
  (:require [clojure.tools.logging :as log])
  (:import [org.h2.jdbcx JdbcDataSource])
  )

(defn ^:private do-sql [ds f]
  (try
    (def con (.getConnection ds))
    {:result (f con)}
    (catch Exception e
      (log/error e "failed execution sql action")
      {:error (.getMessage e)}
      )
    (finally
      (if con (.close con))
      )
    )
  )

(defn ^:private read-rs
  ([rs limit]
   (let [rs-meta (.getMetadata rs)
         col-size (.getColumnCount rs-meta)
         cols (map #(.getColumnName rs-meta %) (range 1 col-size))]
     (loop [rows [] count 0]
       (if (and (.next rs) (< count limit))
         (let [row (map #(.getObject rs %) (range 1 col-size))]
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
       (let [row (map #(.getObject rs %) cols)]
         (recur (conj rows row) (inc count))
         )
       {:columns cols :rows rows}
       )
     )
   )
  )

(defn mk-ds [dbms path user pass]
  "Creates a datasource"
  (if-let [ds (condp = dbms
                "H2" (doto (JdbcDataSource.)
                       (.setUrl (str "jdbc:h2:" path))
                       (.setUser user)
                       (.setPassword pass)))]

    (try
      (def con (.getConnection ds))
      {:datasource ds}
      (catch Exception e
        (println "failed connecting to db" e)
        {:error (.getMessage e)}
        )
      (finally (if con (.close con)))))
  )

(defn tables [ds]
  (do-sql ds (fn [con]
               (-> con
                   .getMetaData
                   (.getTables nil nil "%" nil)
                   (read-rs ["TABLE_NAME"] 100)
                   (:rows))))
  )

(defn table-data
  ([ds table limit]
   (do-sql ds (fn [con] (-> con
                            .createStatement
                            (.executeQuery (str "SELECT * FROM " table))
                            (read-rs limit))))
   )
  ([ds table] (table-data ds table 100)))
