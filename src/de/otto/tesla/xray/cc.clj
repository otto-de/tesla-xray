(ns de.otto.tesla.xray.cc
  (:require [clojure.data.xml :as xml]
            [clj-time.format :as tformat]
            [compojure.core :as comp])
  (:import (org.joda.time DateTime)
           (de.otto.tesla.xray.check XRayCheckResult)))

(defn render-results-xml [check-results]
  (for [[check-id env-to-data] @check-results]
    (for [[env {results :results overall-status :overall-status}] env-to-data]
      (let [^XRayCheckResult result (first results)
            date-time-string (tformat/unparse (tformat/formatters :date-time) (DateTime. (:stop-time result)))]
        (xml/element :Project {:name            (str check-id " on " env)
                               :last-build-time date-time-string
                               :lastBuildStatus (str overall-status)} [])))))

(defn render-xml [{:keys [check-results]}]
  (->> (render-results-xml check-results)
       (xml/element :Projects {})
       (xml/emit-str)))

(defn routes [self]
  (comp/routes
    (comp/GET "/cc.xml" []
      {:status  200
       :headers {"Content-Type" "text/xml"}
       :body    (render-xml self)})))

