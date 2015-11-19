(ns de.otto.tesla.xray.xray-checker
  (:require [com.stuartsierra.component :as c]
            [overtone.at-at :as at]
            [clojure.tools.logging :as log]
            [compojure.core :as comp]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.conf.reading-properties :as props]
            [compojure.route :as croute]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]))

(defprotocol XRayCheckerProtocol
  (register-check [self check checkname])
  (register-check-with-strategy [self check checkname strategy]))

(defrecord RegisteredXRayCheck [check check-name strategy]
  chk/XRayCheck
  (start-check [_ env]
    (chk/start-check check env)))

(defn- append-result [old-results result max-check-history]
  (let [limited-results (take (- max-check-history 1) old-results)]
    (conj limited-results result)))

(defn update-overall-status [{:keys [results] :as result-map} strategy]
  (assoc result-map :overall-status (strategy results)))

(defn- store-check-result [^RegisteredXRayCheck {:keys [strategy]} max-check-history result old-results]
  (-> (or old-results {})
      (update :results append-result result max-check-history)
      (update-overall-status strategy)))

(defn- store-result [check-results ^RegisteredXRayCheck {:keys [check-name] :as check} current-env max-check-history result]
  (let [update-fn (partial store-check-result check max-check-history result)]
    (swap! check-results update-in [check-name current-env] update-fn)))

(defn- current-time []
  (System/currentTimeMillis))

(defn- start-single-xraycheck [max-check-history check-results [^RegisteredXRayCheck check current-env]];TODO
  (try
    (let [start-time (current-time)
          check-result (chk/start-check check current-env)
          stop-time (current-time)
          result-with-timings (chk/with-timings check-result (- stop-time start-time) stop-time)]
      (store-result check-results check current-env max-check-history result-with-timings))
    (catch Exception e
      (log/error e "an error occured when executing check " (:check-name check) (.getMessage e))
      (store-result check-results check current-env max-check-history (chk/->XRayCheckResult :error (.getMessage e))))))

(defn- wrap-with [a-fn]
  (partial deref (future (a-fn))))

(defn- with-evironments [environments check]
  (map (fn [env] [check env]) environments))

(defn- build-check-name-env-vecs [environments checks]
  (->> @checks
       (mapcat (fn [[_ ^RegisteredXRayCheck check]] (with-evironments environments check)))
       (into [])))

(defn- start-the-xraychecks [{:keys [max-check-history check-results checks environments]}]
  (let [pstart-single-xraycheck (partial start-single-xraycheck max-check-history check-results) ;check name env - missing
        checks+env (build-check-name-env-vecs environments checks)
        futures (map #(wrap-with (partial pstart-single-xraycheck %)) checks+env)]
    (log/info "Starting checks")
    (apply pcalls futures)))

(defn- xray-routes [self endpoint]
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
          nr-checks-displayed (props/parse-nr-checks-displayed config which-checker)
          max-check-history (props/parse-max-check-history config which-checker)
          refresh-frequency (props/parse-refresh-frequency config which-checker)
          environments (props/parse-check-environments config which-checker)
          endpoint (props/parse-endpoint config which-checker)
          new-self (assoc self
                     :nr-checks-displayed nr-checks-displayed
                     :max-check-history max-check-history
                     :environments environments
                     :executor executor
                     :checks (atom {})
                     :check-results (atom {}))]
      (hndl/register-handler handler (xray-routes new-self endpoint))
      (log/info "running checks every " refresh-frequency "ms")
      (assoc new-self
        :schedule (at/every refresh-frequency
                            #(start-the-xraychecks new-self)
                            executor))))

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
