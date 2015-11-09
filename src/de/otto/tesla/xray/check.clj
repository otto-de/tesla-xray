(ns de.otto.tesla.xray.check)

(defprotocol XRayCheck
  (start-realtime-check [self env]))

(defrecord XRayCheckResult [status message timestamp])