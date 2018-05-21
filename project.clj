(defproject dbquery "0.8.3-SNAPSHOT"
  :description "A simple platform for maintaining and running db queries"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :ring {:handler dbquery.core/all-routes}
  :repositories [["jitpack" {:url "https://jitpack.io" :snapshots false}]]
  :deploy-repositories [["releases" {:url "file:///tmp/"}]]
  :dependencies [[compojure "1.6.0"]
                 [ring "1.6.3"]
                 [ring/ring-json "0.4.0"]
                 [ring/ring-defaults "0.3.1"]
                 ;; [ring/ring-devel "1.1.8"]
                 [reagent "0.7.0"]
                 [reagent-utils "0.2.1"]
                 [re-frame "0.10.5"]
                 [cljs-ajax "0.7.3"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/clojurescript "1.10.238" :scope "provided"]
                 [secretary "1.2.3"]
                 [http-kit "2.3.0"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [org.slf4j/slf4j-jdk14 "1.7.13"]
                 [korma "0.4.1"]
                 [com.github.rinconjc/db-upgrader "1.0-beta15"]
                 [com.h2database/h2 "1.4.187"]
                 [org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [crypto-password "0.1.3"]
                 [org.clojure/core.cache "0.6.4"]
                 [liberator "0.13"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.jasypt/jasypt "1.9.2"]
                 [cljsjs/react-bootstrap "0.30.7-0" :exclusions [[org.webjars.bower/jquery] [cljsjs/react]]]
                 [cljsjs/mousetrap "1.5.3-0"]
                 [cljsjs/codemirror "5.7.0-1"]
                 [com.zaxxer/HikariCP "2.5.1"]
                 [net.sourceforge.jtds/jtds "1.3.1"]]
  :plugins [[lein-environ "1.0.1"]
            [lein-cljsbuild "1.1.7" :exclusions [[org.clojure/clojure]]]
            [lein-asset-minifier "0.2.7" :exclusions [org.clojure/clojure]]
            [lein-cljsasset "0.2.0"]]
  :resource-paths ["lib/ojdbc6.jar" "resources"]
  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :minify-assets
  {:assets
   {"resources/public/css/main.min.css" "resources/public/css/main.css"}}
  :cljsasset {:css ["cljsjs/codemirror/production/codemirror.min.css"]
              :js  ["cljsjs/codemirror/common/mode/sql.inc.js"]}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs" "src/cljc"]
                             :compiler     {:output-to     "resources/public/js/app.js"
                                            :output-dir    "resources/public/js/out"
                                            :asset-path    "js/out"
                                            :optimizations :none
                                            :pretty-print  true}}}}
  :main dbquery.core
  ;; :target-path "target/%s"
  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :profiles {:dev
             {:repl-options {:init-ns          dbquery.repl
                             :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

              :dependencies [[figwheel-sidecar "0.5.14"]
                             [org.clojure/tools.namespace "0.2.11"]
                             [ring/ring-mock "0.3.2"]
                             [ring/ring-devel "1.6.3"]
                             [lein-figwheel "0.4.0"]
                             [org.clojure/tools.nrepl "0.2.13"]
                             [pjstadig/humane-test-output "0.7.0"]
                             [com.cemerick/piggieback "0.2.2"]]

              :source-paths ["env/dev/clj"]
              :plugins      [[lein-figwheel "0.5.14"]
                             [lein-cljsbuild "1.0.6"]]

              :injections   [(require 'pjstadig.humane-test-output)
                             (pjstadig.humane-test-output/activate!)]

              :figwheel     {:http-server-root "public"
                             :server-port      3450
                             :nrepl-port       7003
                             :css-dirs         ["resources/public/css"]
                             :ring-handler     dbquery.core/all-routes}

              :env          {:dev true}

              :cljsbuild    {:builds {:app {:source-paths ["env/dev/cljs"]
                                            :compiler     {:main       "dbquery.dev"
                                                           :source-map true}}}}}
             :uberjar {:hooks       [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env         {:production true}
                       :aot         :all
                       :omit-source true
                       :cljsbuild   {:jar    true
                                     :builds {:app
                                              {:source-paths ["env/prod/cljs"]
                                               :compiler
                                               {:optimizations :advanced
                                                :pretty-print  false}}}}}})
