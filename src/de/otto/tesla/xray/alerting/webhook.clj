(ns de.otto.tesla.xray.alerting.webhook
  (:require
    [clojure.data.json :as json]
    [clj-http.client :as http]
    [clojure.tools.logging :as log]))

(defn payload [msg] { :text msg })

(defn send-webhook-message! [url msg]
  (try
    (http/post url {:body (json/write-str (payload msg))})
    (catch Exception e
      (log/error e "Error when contacting webhook url: " url " message: " (.getMessage e)))))
