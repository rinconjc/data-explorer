(ns dbquery.utils
  (:require [clojure.tools.logging :as log])
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
