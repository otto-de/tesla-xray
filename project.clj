(defproject de.otto/tesla-xray "0.8.8"
  :description "a component to execute and visualize checks written in clj"
  :url "https://github.com/otto-de/tesla-xray.git"
  :license {:name "Apache License 2.0"
            :url  "http://www.apache.org/license/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [de.otto/tesla-microservice "0.11.16"]
                 [hiccup "1.0.5"]
                 [ring/ring-codec "1.1.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [clj-time "0.14.2"]]
  :test-paths ["test" "test-resources"]
  :lein-release {:deploy-via :clojars}
  :sass {:source-paths ["resources/app/stylesheets/"]
         :target-path "resources/public/stylesheets/"}
  :aliases {"jar" ["do" ["sass4clj" "once"] "jar"]}
  :profiles {:dev {:plugins      [[deraen/lein-sass4clj "0.3.1"]
                                  [lein-release/lein-release "1.0.9"]]
                   :main         de.otto.tesla.xray.testsystem
                   :source-paths ["src" "test"]
                   :dependencies [[me.lomin/component-restart "0.1.2"]
                                  [de.otto/tesla-jetty "0.2.6"]
                                  [ring-mock "0.1.5"]
                                  [ch.qos.logback/logback-classic "1.2.3"]
                                  [clj-cctray "1.0.0"]]}})
