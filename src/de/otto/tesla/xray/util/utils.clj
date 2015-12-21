(ns de.otto.tesla.xray.util.utils
  (:require [clojure.tools.logging :as log])
  (:import (java.util.concurrent TimeoutException TimeUnit)))

(defn current-time []
  (System/currentTimeMillis))

(defmacro execute-with-timeout [millis fallback & body]
  `(let [future# (future ~@body)]
     (try
       (.get future# ~millis TimeUnit/MILLISECONDS)
       (catch TimeoutException x#
         (do
           (when-not (future-cancel future#)
             (log/error "Was not able to cancel future..."))
           ~fallback)))))

