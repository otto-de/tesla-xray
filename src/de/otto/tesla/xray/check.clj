(ns de.otto.tesla.xray.check)

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
