(ns de.otto.tesla.xray.check
  (:require [clojure.tools.logging :as log]
            [de.otto.tesla.xray.util.utils :as utils]))

(defprotocol XRayCheck
  (start-check [self env]))

(defprotocol XRayCheckResultProtocol
  (with-timings [self time-taken stop-time]))

(defrecord XRayCheckResult [status message time-taken stop-time]
  XRayCheckResultProtocol
  (with-timings [^XRayCheckResult {new-status :status new-message :message} new-time-taken new-stop-time]
    (->XRayCheckResult new-status new-message new-time-taken new-stop-time)))

(defn ->XRayCheckResult
  ([status message time-taken stop-time]
   (map->XRayCheckResult {:status status :message message :time-taken time-taken :stop-time stop-time}))
  ([status message]
   (map->XRayCheckResult {:status status :message message})))

(defrecord RegisteredXRayCheck [check check-id title strategy]
  XRayCheck
  (start-check [_ env]
    (start-check check env)))

(defn- check-result [xray-check current-env]
  (try
    (or
      (start-check xray-check current-env)
      (->XRayCheckResult :warning "no xray-result returned by check"))
    (catch Throwable t
      (log/info t "Exception thrown in check " (:check-id xray-check))
      (->XRayCheckResult :error (.getMessage t)))))

(defn check-result-with-timings [[^RegisteredXRayCheck xray-check current-env]]
  (let [start-time (utils/current-time)
        check-result (check-result xray-check current-env)
        stop-time (utils/current-time)]
    (with-timings check-result (- stop-time start-time) stop-time)))
