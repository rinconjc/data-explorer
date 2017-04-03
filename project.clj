(defproject dbquery "0.1.0-SNAPSHOT"
  :description "A simple platform for maintaining and running db queries"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :ring {:handler dbquery.core/all-routes}
  :repositories [["jitpack" {:url "https://jitpack.io" :snapshots false}]]
  :dependencies [[compojure "1.4.0"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-defaults "0.1.5"]
                 [ring/ring-devel "1.1.8"]
                 [reagent "0.6.0-alpha"]
                 ;; [reagent-forms "0.5.13"]
                 [re-frame "0.7.0"]
                 [reagent-utils "0.1.5"]
                 [cljs-ajax "0.5.1"]
                 [hiccup "1.0.5"]
                 [environ "1.0.1"]
                 [org.clojure/clojurescript "1.7.145" :scope "provided"]
                 [secretary "1.2.3"]
                 [http-kit "2.1.19"]
                 [org.clojure/clojure "1.7.0"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-jdk14 "1.7.13"]
                 [korma "0.4.1"]
                 [com.github.rinconjc/db-upgrader "1.0-beta15"]
                 ;; [com.jolbox/bonecp "0.8.0.RELEASE" :exclusions [com.google.guava/guava]]
                 [com.h2database/h2 "1.4.187"]
                 [org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [org.clojure/java.jdbc "0.3.6"]
                 [crypto-password "0.1.3"]
                 [org.clojure/core.cache "0.6.4"]
                 [liberator "0.13"]
                 [org.clojure/data.csv "0.1.2"]
                 [org.jasypt/jasypt "1.9.2"]
                 ;; [com.oracle/ojdbc16 "11.2.0.3"]
                 [ojdbc/ojdbc "14"]
                 [cljsjs/react-bootstrap "0.27.3-0" :exclusions [org.webjars.bower/jquery]]
                 [cljsjs/mousetrap "1.5.3-0"]
                 [cljsjs/codemirror "5.7.0-1"]
                 [com.zaxxer/HikariCP "2.5.1"]
                 [net.sourceforge.jtds/jtds "1.3.1"]]
  :plugins [[lein-ring "0.9.7"]
            [lein-environ "1.0.1"]
            [lein-asset-minifier "0.2.2"]
            [lein-cljsasset "0.2.0"]]
  :resource-paths ["lib/*" "resources"]
  :source-paths ["src/clj" "src/cljc"]
  :hooks [leiningen.cljsbuild]
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
             {:repl-options
              {:init-ns          dbquery.repl
               :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

              :dependencies [[figwheel-sidecar "0.5.4-3"]
                             [org.clojure/tools.namespace "0.2.11"]
                             [ring/ring-mock "0.3.0"]
                             [ring/ring-devel "1.4.0"]
                             [lein-figwheel "0.4.0"]
                             [org.clojure/tools.nrepl "0.2.12"]
                             [pjstadig/humane-test-output "0.7.0"]
                             [com.cemerick/piggieback "0.2.1"]]

              :source-paths ["env/dev/clj"]
              :plugins      [[lein-figwheel "0.5.4-3"]
                             [lein-cljsbuild "1.0.6"]]

              :injections   [(require 'pjstadig.humane-test-output)
                             (pjstadig.humane-test-output/activate!)]

              :figwheel     {:http-server-root "public"
                             :server-port      3449
                             :nrepl-port       7002
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
