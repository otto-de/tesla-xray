(ns de.otto.tesla.xray.conf.reading-properties)

(defn parse-check-environments [config which-checker]
  (let [env-str (get-in config [:config (keyword (str which-checker "-check-environments"))] "")]
    (if (empty? env-str) [] (clojure.string/split env-str #";"))))

(defn parse-refresh-frequency [config which-checker]
  (let [frequency (get-in config [:config (keyword (str which-checker "-check-frequency"))] "60000")]
    (if-not (empty? frequency)
      (Integer/parseInt frequency))))

(defn parse-endpoint [config which-checker]
  (get-in config [:config (keyword (str which-checker "-check-endpoint"))] "/xray-checker"))

(defn parse-max-check-history [config which-checker]
  (Integer/parseInt (get-in config [:config (keyword (str which-checker "-max-check-history"))] "100")))

(defn parse-nr-checks-displayed [config which-checker]
  (Integer/parseInt (get-in config [:config (keyword (str which-checker "-nr-checks-displayed"))] "5")))

(defn parse-hours-to-expire [config which-checker]
  (get-in config [:config (keyword (str which-checker "-acknowledge-hours-to-expire"))] 24))
