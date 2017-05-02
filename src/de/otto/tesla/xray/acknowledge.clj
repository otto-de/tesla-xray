(ns de.otto.tesla.xray.acknowledge
  (:require [clojure.data.json :as json]
            [de.otto.tesla.xray.util.utils :as utils]
            [compojure.core :as comp])
  (:import (org.joda.time.format DateTimeFormat)
           (org.joda.time DateTime)))


(defn acknowledgement-active? [[_ end-time]]
  (> end-time (utils/current-time)))

(defn with-cleared-acknowledgement-entry [new-state [check-id env-acknowledgements]]
  (if-let [filtered (seq (filter acknowledgement-active? env-acknowledgements))]
    (assoc new-state check-id (into {} filtered))
    new-state))

(defn clear-outdated-acknowledgements! [{:keys [acknowledged-checks]}]
  (swap! acknowledged-checks #(reduce with-cleared-acknowledgement-entry {} %)))


(defn acknowledge-check! [{:keys [check-results acknowledged-checks]} check-id environment duration-in-hours]
  (let [duration-in-ms (* 60 60 1000 (Long/parseLong duration-in-hours))]
    (swap! acknowledged-checks assoc-in [check-id environment] (+ duration-in-ms (utils/current-time)))
    (swap! check-results assoc-in [check-id environment :overall-status] :acknowledged)))

(defn remove-acknowledgement! [{:keys [acknowledged-checks]} check-id environment]
  (swap! acknowledged-checks update check-id dissoc environment)
  (swap! acknowledged-checks (fn [x] (into {} (filter #(not-empty (second %)) x)))))

(defn as-date-time [millis]
  (DateTime. millis))

(defn as-readable-time [millis]
  (.toString (as-date-time millis) (DateTimeFormat/forPattern "d MMMM, hh:mm")))

(defn stringify-acknowledged-checks [{:keys [acknowledged-checks]}]
  (let [format-time (fn [_ value]
                      (if (number? value)
                        (as-readable-time value)
                        value))]
    (json/write-str @acknowledged-checks :value-fn format-time)))

(defn routes [self endpoint]
  (comp/routes
    (comp/GET (str endpoint "/acknowledged-checks") []
      {:status  200
       :headers {"Content-Type" "application/json"}
       :body    (stringify-acknowledged-checks self)})

    (comp/POST (str endpoint "/acknowledged-checks") [check-id environment hours]
      (acknowledge-check! self check-id environment hours)
      {:status  204
       :headers {"Content-Type" "text/plain"}
       :body    ""})

    (comp/DELETE (str endpoint "/acknowledged-checks/:check-id/:environment") [check-id environment]
      (remove-acknowledgement! self check-id environment)
      {:status  204
       :headers {"Content-Type" "text/plain"}
       :body    ""})))
