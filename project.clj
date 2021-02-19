(defproject dbquery "0.8.3-SNAPSHOT"
  :description "A simple platform for maintaining and running db queries"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  ;; :ring {:handler dbquery.core/app}
  :repositories [["jitpack" {:url "https://jitpack.io" :snapshots false}]]
  :deploy-repositories [["releases" {:url "file:///tmp/"}]]
  :dependencies [[compojure "1.6.1"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.3.1"]
                 ;; [ring/ring-devel "1.1.8"]
                 [reagent "0.8.1"]
                 [reagent-utils "0.3.2"]
                 [re-frame "0.10.6"]
                 [cljs-ajax "0.8.0"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/clojurescript "1.10.520" :scope "provided"]
                 [secretary "1.2.3"]
                 [http-kit "2.3.0"]
                 [org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-jdk14 "1.7.13"]
                 [korma "0.4.3"]
                 [com.h2database/h2 "1.4.200"]
                 [org.postgresql/postgresql "42.2.6"]
                 [org.clojure/java.jdbc "0.7.10"]
                 [crypto-password "0.1.3"]
                 [org.clojure/core.cache "0.6.4"]
                 [liberator "0.15.2"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.jasypt/jasypt "1.9.2"]
                 [cljsjs/react-bootstrap "0.32.4-0"
                  :exclusions [[org.webjars.bower/jquery] [cljsjs/react]]]
                 [cljsjs/mousetrap "1.6.2-0"]
                 [com.zaxxer/HikariCP "2.7.2"]
                 [net.sourceforge.jtds/jtds "1.3.1"]
                 [com.mysql/connectorj "5.1.12"]
                 [thi.ng/geom "1.0.0-RC3"]
                 [binaryage/oops "0.7.0"]
                 [org.clojure/core.async "1.3.610"]
                 [yogthos/config "1.1.7"]
                 [clucy "0.4.2-SNAPSHOT"]
                 ;; https://mvnrepository.com/artifact/org.liquibase/liquibase-core
                 [org.liquibase/liquibase-core "4.3.1"]]
  :plugins [[lein-environ "1.0.1"]
            [lein-asset-minifier "0.4.6" :exclusions [org.clojure/clojure]]
            [lein-cljsasset "0.2.0"]]
  :resource-paths ["lib/ojdbc6.jar" "resources"]
  :source-paths ["src/clj" "src/cljc" "src/cljs"]

  :main dbquery.core
  :manifest {"Class-Path" "lib/*"}

  ;; :jvm-opts ["-Dconf=conf.edn"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["with-profile" "dev" "run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" dbquery.test-runner]}

  :profiles {:dev
             {:repl-options {:init (start-server 3001)}
              :dependencies [[com.bhauman/figwheel-main "0.2.0"]
                             [com.bhauman/rebel-readline-cljs "0.1.4"]
                             [com.facebook.presto/presto-jdbc "0.244.1"]]

              }
             :uberjar {:hooks       [minify-assets.plugin/hooks]
                       :env         {:production true}
                       :aot         :all
                       :omit-source true
                       :prep-tasks ["compile" "fig:min"]}})
