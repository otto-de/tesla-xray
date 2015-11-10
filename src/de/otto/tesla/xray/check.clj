(ns de.otto.tesla.xray.check)

(defprotocol XRayCheck
  (start-check [self env]))

(defrecord XRayCheckResult [status message timestamp])