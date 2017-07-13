(defproject de.otto/tesla-xray "0.8.4-SNAPSHOT"
  :description "a component to execute and visualize checks written in clj"
  :url "https://github.com/otto-de/tesla-xray.git"
  :license {:name "Apache License 2.0"
            :url  "http://www.apache.org/license/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [de.otto/tesla-microservice "0.11.2"]
                 [hiccup "1.0.5"]
                 [ring/ring-codec "1.0.1"]
                 [org.clojure/data.xml "0.0.8"]
                 [clj-time "0.13.0"]]
  :test-paths ["test" "test-resources"]
  :lein-release {:deploy-via :clojars}
  :sass {:src "resources/app/stylesheets"
         :dst "target/public/stylesheets"}
  :aliases {"jar" ["do" ["sass" "once"] "jar"]}
  :profiles {:dev {:plugins      [[lein-sassy "1.0.8"]
                                  [lein-release/lein-release "1.0.9"]]
                   :main         de.otto.tesla.xray.testsystem
                   :source-paths ["src" "test"]
                   :dependencies [[me.lomin/component-restart "0.1.2"]
                                  [de.otto/tesla-jetty "0.1.3"]
                                  [ring-mock "0.1.5"]
                                  [ch.qos.logback/logback-classic "1.2.3"]
                                  [clj-cctray "0.12.0"]]}})
