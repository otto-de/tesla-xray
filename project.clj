(defproject de.otto/tesla-xray "0.2.19"
  :description "a component to execute and visualize checks written in clj"
  :url "https://github.com/otto-de/tesla-xray.git"
  :license {:name "Apache License 2.0"
            :url  "http://www.apache.org/license/LICENSE-2.0.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [overtone/at-at "1.2.0"]
                 [hiccup "1.0.5"]
                 [ring/ring-codec "1.0.0"]
                 [clj-time "0.11.0"]]

  :provided {:dependencies [[de.otto/tesla-basic-logging "0.1.4"]
                            [de.otto/tesla-microservice "0.1.18"]]}

  :profiles {:dev {:plugins      [[lein-ancient "0.5.4"]]
                   :main de.otto.tesla.xray.testsystem
                   :source-paths ["src" "test"]
                   :dependencies [[de.otto/tesla-microservice "0.1.18"]
                                  [me.lomin/component-restart "0.1.0"]
                                  [de.otto/tesla-jetty "0.1.0"]
                                  [ring-mock "0.1.5"]]}})
