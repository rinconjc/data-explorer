(defproject dbquery "0.1.0-SNAPSHOT"
  :description "A simple platform for maintaining and running db queries"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-ring "0.8.11"]]
  :ring {:handler dbquery.core/all-routes}
  :dependencies [[compojure "1.3.4"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [korma "0.4.1"]
                 [com.rinconj/dbupgrader "1.0-SNAPSHOT"]
                 [com.h2database/h2 "1.4.187"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [crypto-password "0.1.3"]
                 [org.clojure/core.cache "0.6.4"]]
  :resource-paths ["lib/*" "resources"]
  :main ^:skip-aot dbquery.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}}
  )
