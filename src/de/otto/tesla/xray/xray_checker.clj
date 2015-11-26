(ns de.otto.tesla.xray.xray-checker
  (:require [com.stuartsierra.component :as c]
            [overtone.at-at :as at]
            [clojure.tools.logging :as log]
            [compojure.core :as comp]
            [de.otto.tesla.xray.ui.detail-page :as dp]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.ui.overall-status :as oas]
            [de.otto.tesla.xray.conf.reading-properties :as props]
            [compojure.route :as croute]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]))

(defprotocol XRayCheckerProtocol
  (set-alerting-function [self alerting-fn])
  (register-check [self check checkname])
  (register-check-with-strategy [self check checkname strategy]))

(defrecord RegisteredXRayCheck [check check-name strategy]
  chk/XRayCheck
  (start-check [_ env]
    (chk/start-check check env)))

(defn send-alerts! [alerting-function results check-name current-env]
  (try
    (alerting-function {:result     (first results)
                        :check-name check-name
                        :env        current-env})
    (catch Exception e
      (log/error e "Error when calling alerting function"))))

(defn should-send-another-alert? [schedule-time last-alert]
  (or (nil? last-alert)
      (> (- (utils/current-time) last-alert)
         schedule-time)))

(defn do-alerting! [alerting-fn check-results {:keys [schedule-time]} check-name current-env]
  (when-let [alerting-function @alerting-fn]
    (let [{:keys [results overall-status last-alert]} (get-in @check-results [check-name current-env])]
      (when (and
              (= :error overall-status)
              (should-send-another-alert? schedule-time last-alert))
        (send-alerts! alerting-function results check-name current-env)
        (swap! check-results assoc-in [check-name current-env :last-alert] (utils/current-time))))))

(defn update-overall-status [{:keys [results] :as result-map} strategy]
  (let [new-status (strategy results)]
    (assoc result-map :overall-status new-status)))

(defn- append-result [old-results result max-check-history]
  (let [limited-results (take (- max-check-history 1) old-results)]
    (conj limited-results result)))

(defn- update+handle-result! [result {:keys [max-check-history]} strategy old-results]
  (-> (or old-results {})
      (update :results append-result result max-check-history)
      (update-overall-status strategy)))

(defn- update-results! [{:keys [alerting-fn check-results xray-config]} {:keys [check-name strategy]} current-env result]
  (let [update-fn (partial update+handle-result! result xray-config strategy)]
    (swap! check-results update-in [check-name current-env] update-fn)
    (do-alerting! alerting-fn check-results (:alerting xray-config) check-name current-env)))

(defn- check-result [xray-check current-env]
  (try
    (or
      (chk/start-check xray-check current-env)
      (chk/->XRayCheckResult :warning "no xray-result returned by check"))
    (catch Throwable t
      (chk/->XRayCheckResult :error (.getMessage t)))))

(defn- check-result-with-timings [xray-check current-env]
  (let [start-time (utils/current-time)
        check-result (check-result xray-check current-env)
        stop-time (utils/current-time)]
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

(defn- start-the-xraychecks [{:keys [last-check checks xray-config] :as self}]
  (let [pstart-single-xraycheck (partial start-single-xraycheck self)
        checks+env (build-check-name-env-vecs (:environments xray-config) checks)
        futures (map #(wrap-with (partial pstart-single-xraycheck %)) checks+env)]
    (log/info "Starting checks")
    (apply pcalls futures)
    (reset! last-check (utils/current-time))))

(defn- xray-routes [{:keys [check-results last-check xray-config]}]
  (let [{:keys [endpoint]} xray-config]
    (comp/routes
      (croute/resources "/")
      (comp/GET endpoint []
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (oas/render-overall-status check-results last-check xray-config)})

      (comp/GET (str endpoint "/overview") []
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (eo/render-env-overview check-results xray-config)})

      (comp/GET (str endpoint "/detail/:check-name/:environment") [check-name environment]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (dp/render-detail-page check-results xray-config check-name environment)}))))

(defn default-strategy [results]
  (:status (first results)))

(defrecord XrayChecker [which-checker handler config registered-checks]
  c/Lifecycle
  (start [self]
    (log/info "-> starting XrayChecker")
    (let [executor (at/mk-pool)
          new-self (assoc self
                     :xray-config {:refresh-frequency   (props/parse-refresh-frequency config which-checker)
                                   :nr-checks-displayed (props/parse-nr-checks-displayed config which-checker)
                                   :max-check-history   (props/parse-max-check-history config which-checker)
                                   :endpoint            (props/parse-endpoint config which-checker)
                                   :environments        (props/parse-check-environments config which-checker)
                                   :alerting            {:schedule-time (props/parse-alerting-schedule-time config which-checker)}}
                     :executor executor
                     :alerting-fn (atom nil)
                     :last-check (atom nil)
                     :checks (atom {})
                     :check-results (atom {}))
          frequency (get-in new-self [:xray-config :refresh-frequency])]
      (hndl/register-handler handler (xray-routes new-self))
      (log/info "running checks every " frequency "ms")
      (if frequency
        (assoc new-self
          :schedule (at/every frequency
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
  (set-alerting-function [{:keys [alerting-fn]} new-alerting-fn]
    (reset! alerting-fn new-alerting-fn))

  (register-check [self check checkname]
    (register-check-with-strategy self check checkname default-strategy))

  (register-check-with-strategy [self check checkname strategy]
    (log/info "registering check with name: " checkname)
    (swap! (:checks self) assoc checkname (->RegisteredXRayCheck check checkname strategy))))

(defn new-xraychecker [which-checker]
  (map->XrayChecker {:which-checker which-checker}))
