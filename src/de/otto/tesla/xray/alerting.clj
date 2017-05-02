(ns de.otto.tesla.xray.alerting
  (:require [clojure.tools.logging :as log]))

(defn send-alerts! [alerting-function check-id current-env results overall-status]
  (try
    (alerting-function {:last-result    (first results)
                        :overall-status overall-status
                        :check-id       check-id
                        :env            current-env})
    (catch Exception e
      (log/error e "Error when calling alerting function"))))

(defn do-alerting! [{:keys [alerting-fn check-results]} check-id current-env overall-status]
  (when-let [alerting-function @alerting-fn]
    (let [results (get-in @check-results [check-id current-env :results])]
      (send-alerts! alerting-function check-id current-env results overall-status))))

(defn- existing-status-has-changed? [overall-status new-overall-status]
  (and (some? overall-status)
       (not= overall-status new-overall-status)))

(defn- initial-status-is-failure? [overall-status new-overall-status]
  (and (nil? overall-status)
       (not= :ok new-overall-status)))

(defn- alerting-needed? [old-overall-status new-overall-status]
  (or (existing-status-has-changed? old-overall-status new-overall-status)
      (initial-status-is-failure? old-overall-status new-overall-status)))

(defn do-alerting-or-not [{:keys [check-results] :as self} check-id current-env old-overall-status]
  (let [new-overall-status (get-in @check-results [check-id current-env :overall-status])]
    (when (alerting-needed? old-overall-status new-overall-status)
      (do-alerting! self check-id current-env new-overall-status))))
