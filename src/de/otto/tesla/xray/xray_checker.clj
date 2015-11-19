(ns de.otto.tesla.xray.xray-checker
  (:require [com.stuartsierra.component :as c]
            [overtone.at-at :as at]
            [clojure.tools.logging :as log]
            [compojure.core :as comp]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.conf.reading-properties :as props]
            [compojure.route :as croute]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]
            [de.otto.tesla.xray.alerting.webhook :as webh]))

(defprotocol XRayCheckerProtocol
  (register-check [self check checkname])
  (register-check-with-strategy [self check checkname strategy]))

(defrecord RegisteredXRayCheck [check check-name strategy]
  chk/XRayCheck
  (start-check [_ env]
    (chk/start-check check env)))

(defn- current-time []
  (System/currentTimeMillis))

(defn- append-result [old-results result max-check-history]
  (let [limited-results (take (- max-check-history 1) old-results)]
    (conj limited-results result)))

(defn send-alerts! [{:keys [results]} {:keys [incoming-webhook]}]
  (let [last-result-message (:message (first results))]
    (when incoming-webhook
      (webh/send-webhook-message! incoming-webhook last-result-message))))

(defn should-send-another-alert? [schedule-time last-alert]
  (or (nil? last-alert)
      (> (- (current-time) last-alert)
         schedule-time)))

(defn do-alerting! [{:keys [overall-status last-alert] :as result-map} {:keys [schedule-time] :as alerting}]
  (if (= :error overall-status)
    (if (should-send-another-alert? schedule-time last-alert)
      (doto (assoc result-map :last-alert (current-time))
        (send-alerts! alerting))
      result-map)
    (dissoc result-map :last-alert)))

(defn update-overall-status [{:keys [results] :as result-map} strategy]
  (let [new-status (strategy results)]
    (assoc result-map :overall-status new-status)))

(defn- update+handle-result! [{:keys [max-check-history alerting]} ^RegisteredXRayCheck {:keys [strategy]} result old-results]
  (-> (or old-results {})
      (update :results append-result result max-check-history)
      (update-overall-status strategy)
      (do-alerting! alerting)))

(defn- update-results! [{:keys [check-results] :as self} ^RegisteredXRayCheck {:keys [check-name] :as check} current-env result]
  (let [update-fn (partial update+handle-result! self check result)]
    (swap! check-results update-in [check-name current-env] update-fn)))

(defn- check-result-with-timings [xray-check current-env]
  (let [start-time (current-time)
        check-result (chk/start-check xray-check current-env)
        stop-time (current-time)]
    (chk/with-timings check-result (- stop-time start-time) stop-time)))

(defn- start-single-xraycheck [self [^RegisteredXRayCheck xray-check current-env]]
  (try
    (let [result (check-result-with-timings xray-check current-env)]
      (update-results! self xray-check current-env result))
    (catch Exception e
      (log/error e "an error occured when executing check " (:check-name xray-check) (.getMessage e))
      (update-results! self xray-check current-env (chk/->XRayCheckResult :error (.getMessage e))))))

(defn- wrap-with [a-fn]
  (partial deref (future (a-fn))))

(defn- with-evironments [environments check]
  (map (fn [env] [check env]) environments))

(defn- build-check-name-env-vecs [environments checks]
  (->> @checks
       (mapcat (fn [[_ ^RegisteredXRayCheck check]] (with-evironments environments check)))
       (into [])))

(defn- start-the-xraychecks [{:keys [checks environments] :as self}]
  (let [pstart-single-xraycheck (partial start-single-xraycheck self)
        checks+env (build-check-name-env-vecs environments checks)
        futures (map #(wrap-with (partial pstart-single-xraycheck %)) checks+env)]
    (log/info "Starting checks")
    (apply pcalls futures)))

(defn- xray-routes [{:keys [endpoint] :as self}]
  (comp/routes
    (croute/resources "/")
    (comp/GET endpoint []
      {:status  200
       :headers {"Content-Type" "text/html"}
       :body    (eo/render-env-overview self)})))

(defn default-strategy [results]
  (:status (first results)))

(defrecord XrayChecker [which-checker handler config registered-checks]
  c/Lifecycle
  (start [self]
    (log/info "-> starting XrayChecker")
    (let [executor (at/mk-pool)
          new-self (assoc self
                     :refresh-frequency (props/parse-refresh-frequency config which-checker)
                     :nr-checks-displayed (props/parse-nr-checks-displayed config which-checker)
                     :max-check-history (props/parse-max-check-history config which-checker)
                     :endpoint (props/parse-endpoint config which-checker)
                     :alerting {:schedule-time    (props/parse-alerting-schedule-time config which-checker)
                                :incoming-webhook (props/parse-incoming-webhook-url config which-checker)}
                     :environments (props/parse-check-environments config which-checker)
                     :executor executor
                     :checks (atom {})
                     :check-results (atom {}))]
      (hndl/register-handler handler (xray-routes new-self))
      (log/info "running checks every " (:refresh-frequency new-self) "ms")
      (if (:refresh-frequency new-self)
        (assoc new-self
          :schedule (at/every (:refresh-frequency new-self)
                              #(start-the-xraychecks new-self)
                              executor))
        new-self)))

  (stop [self]
    (log/info "<- stopping XrayChecker")
    (when-let [job (:schedule self)]
      (at/kill job))
    (at/stop-and-reset-pool! (:executor self))
    self)

  XRayCheckerProtocol
  (register-check [self check checkname]
    (register-check-with-strategy self check checkname default-strategy))

  (register-check-with-strategy [self check checkname strategy]
    (log/info "registering check with name: " checkname)
    (swap! (:checks self) assoc checkname (->RegisteredXRayCheck check checkname strategy))))

(defn new-xraychecker [which-checker]
  (map->XrayChecker {:which-checker which-checker}))
