(ns dbquery.core
  (:import [java.util Date])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.json :refer :all]
            [ring.util.response :refer [response redirect]]
            [ring.middleware.defaults :refer :all]
            [org.httpkit.server :refer [run-server]]
            [dbquery.databases :refer :all]))

(defroutes static
  (route/resources "/"))

(defroutes app
  (GET "/ping" [] (fn [req] (format "replied at %s" (Date.))))

  (GET "/" [] (redirect "/index.html"))

  (POST "/data-source" [] (fn [req] (let [{dbms :dbms dbpath :dbpath dbuser :user dbpass :password} (:body req)
                                          ds-res (mk-ds dbms dbpath dbuser dbpass)]
                                      (if-let [ds (:datasource ds-res)]
                                        (do (assoc (:session req) :cur-ds ds)
                                            "datasource created")
                                        {:status 400 :body (:error ds-res)}
                                        )
                                      ))
        )
  
  (GET "/tables" [req] (tables (get-in req [:session :cur-ds])))

  (GET "/tables/:name" [name] (fn [req] (table-data (get-in req [:session :cur-ds]) name)))
  
  )
  

(defroutes all-routes
  static
  (-> app
      (wrap-defaults api-defaults)
      (wrap-defaults (assoc site-defaults :security {:anti-forgery false}))
      (wrap-json-body {:keywords? true})))
;;

(defn -main []
  (run-server all-routes {:port 5000}))
