(defproject dbquery "0.1.0-SNAPSHOT"
  :description "A simple platform for maintaining and running db queries"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[compojure "1.3.4"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-defaults "0.1.5"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.6.0"]
                 [korma "0.4.0"]
                 [com.rinconj/dbupgrader "1.0-SNAPSHOT"]
                 [com.h2database/h2 "1.4.187"]]
  :main ^:skip-aot dbquery.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
