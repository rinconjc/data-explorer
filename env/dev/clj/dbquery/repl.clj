(ns dbquery.repl
  (:require [dbquery.core :refer [start-server]]))

(println "repl called...")
(start-server "3001")
