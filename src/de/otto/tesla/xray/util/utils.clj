(ns de.otto.tesla.xray.util.utils
  (:require [clojure.tools.logging :as log]
            [clj-time.format :as f]
            [clj-time.coerce :as time])
  (:import (java.util.concurrent TimeoutException TimeUnit)
           (org.joda.time DateTimeZone)))

(defn current-time []
  (System/currentTimeMillis))

(def date-format (f/formatter "yyyy.MM.dd H:mm:ss" (DateTimeZone/forID "Europe/Berlin")))

(defmacro execute-with-timeout [millis fallback & body]
  `(let [future# (future ~@body)]
     (try
       (.get future# ~millis TimeUnit/MILLISECONDS)
       (catch TimeoutException x#
         (do
           (when-not (future-cancel future#)
             (log/warn "Was not able to cancel future..."))
           ~fallback)))))

(defn readable-timestamp [millis]
  (when millis
    (str (f/unparse date-format (time/from-long millis)))))
