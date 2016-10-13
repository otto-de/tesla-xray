(defproject de.otto/tesla-xray "0.3.26-SNAPSHOT"
  :description "a component to execute and visualize checks written in clj"
  :url "https://github.com/otto-de/tesla-xray.git"
  :license {:name "Apache License 2.0"
            :url  "http://www.apache.org/license/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [overtone/at-at "1.2.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-codec "1.0.1"]
                 [clj-time "0.12.0"]]
  :test-paths ["test" "test-resources"]
  :lein-release {:deploy-via :clojars}
  :profiles {:dev {:plugins      [[lein-ancient "0.6.10"][lein-release/lein-release "1.0.9"]]
                   :main de.otto.tesla.xray.testsystem
                   :source-paths ["src" "test"]
                   :dependencies [[de.otto/tesla-microservice "0.3.36"]
                                  [me.lomin/component-restart "0.1.1"]
                                  [de.otto/tesla-jetty "0.1.2"]
                                  [ring-mock "0.1.5"]]}})
