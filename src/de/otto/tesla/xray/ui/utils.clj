(ns de.otto.tesla.xray.ui.utils
  (:require [clj-time.coerce :as time]
            [clj-time.format :as f])
  (:import (org.joda.time DateTimeZone)))

(defn overall-status-ok? [[_env {:keys [overall-status]}]]
  (contains? #{:ok :none} overall-status))

(defn all-ok? [[_check-id all-env-result]]
  (every? overall-status-ok? all-env-result))

(defn separate-completely-ok-checks [check-results]
  (->> check-results
       (group-by all-ok?)
       (map (fn [[k v]] [(if k :all-ok :some-not-ok) (into {} v)]))
       (into {})))

(defn sort-results-by-env [results-for-env environments]
  (sort-by (fn [[env _]] (.indexOf environments env)) results-for-env))

(defn time-left [end-time]
  (let [millis (max (- end-time (System/currentTimeMillis)) 0)
        seconds (int (mod (/ millis 1000) 60))
        minutes (int (mod (/ millis (* 1000 60)) 60))
        hours (int (mod (/ millis (* 1000 60 60)) 24))]
    (format "%sh %smin %ssec" hours minutes seconds)))

(def date-format (f/formatter "yyyy.MM.dd H:mm:ss" (DateTimeZone/forID "Europe/Berlin")))

(defn readable-timestamp [millis]
  (when millis
    (str (f/unparse date-format (time/from-long millis)))))