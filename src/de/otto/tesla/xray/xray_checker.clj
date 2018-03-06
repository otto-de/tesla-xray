(ns de.otto.tesla.xray.xray-checker
  (:require [com.stuartsierra.component :as c]
            [clojure.string :as cs]
            [clojure.tools.logging :as log]
            [overtone.at-at :as at]
            [compojure.core :as comp]
            [compojure.route :as croute]
            [compojure.handler :as chandler]
            [de.otto.tesla.stateful.scheduler :as sched]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.conf.reading-properties :as props]
            [de.otto.tesla.xray.check :as chk]
            [de.otto.tesla.xray.acknowledge :as acknowledge]
            [de.otto.tesla.xray.cc :as cc]
            [de.otto.tesla.xray.alerting :as alerting]
            [de.otto.tesla.xray.ui.routes :as ui]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.xray.check :as chk]))

(defprotocol XRayCheckerProtocol
  (set-alerting-function [self alerting-fn])
  (register-check [self check check-id] [self check check-id title])
  (register-check-with-strategy [self check check-id strategy] [self check check-id title strategy]))

(defn- append-result [old-results result max-check-history]
  (let [limited-results (take (dec max-check-history) old-results)]
    (conj limited-results result)))

(defn- update-results! [{:keys [check-results xray-config acknowledged-checks] :as self} {:keys [check-id strategy]} current-env result]
  (let [{:keys [max-check-history]} xray-config
        {:keys [results overall-status]} (get-in @check-results [check-id current-env])
        acknowledged? (contains? (get @acknowledged-checks check-id) current-env)
        enriched-result (if acknowledged?
                          (assoc result :status :acknowledged :message (str (:message result) "; Acknowledged"))
                          result)
        new-results (append-result results enriched-result max-check-history)
        new-overall-status (if acknowledged? :acknowledged (strategy new-results))]
    (swap! check-results assoc-in [check-id current-env :results] new-results)
    (swap! check-results assoc-in [check-id current-env :overall-status] new-overall-status)
    (alerting/do-alerting-or-not self check-id current-env overall-status)))


(defn- combine-each-check-and-env [environments registered-checks]
  (for [check (vals registered-checks)
        environment environments]
    [check environment]))

(defn- timeout-response [check-id timeout]
  (chk/->XRayCheckResult :error (str check-id " did not finish in " timeout " ms") timeout (utils/current-time)))

(defn- start-future [timeout check+env]
  (let [check-id (:check-id (first check+env))
        fallback (timeout-response check-id timeout)
        started-check-future (future (utils/execute-with-timeout timeout fallback (chk/check-result-with-timings check+env)))]
    [check+env started-check-future]))

(defn collect-results! [self [[xray-check current-env] f]]
  (update-results! self xray-check current-env (deref f)))

(defn execute-checks! [{:keys [xray-config] :as self} coll]
  (->> coll
       (map #(start-future (:refresh-frequency xray-config) %))
       (run! #(collect-results! self %))))

(defn- start-checks [{:keys [last-check registered-checks xray-config] :as self}]
  (try
    (acknowledge/clear-outdated-acknowledgements! self)
    (->> @registered-checks
         (combine-each-check-and-env (:environments xray-config))
         (execute-checks! self))
    (reset! last-check (utils/current-time))
    (catch Exception e
      (log/error e "caught error when trying to start the xraychecks"))))

(defn trigger-routes [{:keys [xray-config registered-checks] :as self}]
  (let [endpoint (get xray-config :endpoint)]
    (comp/routes
      (comp/POST (str endpoint "/trigger-check") [check-id environment]
        (execute-checks! self [[(get @registered-checks check-id) environment]])
        {:status  204
         :headers {"Content-Type" "text/plain"}
         :body    ""}))))

(defn- xray-routes [self]
  (chandler/api
    (comp/routes
      (croute/resources "/")
      (ui/routes self)
      (acknowledge/routes self)
      (trigger-routes self)
      (cc/routes self))))

(defn default-strategy [results]
  (:status (first results)))

(defn cleanup-id [name]
  (cs/replace name #"\W" ""))

(defrecord XrayChecker [which-checker scheduler handler config registered-checks]
  c/Lifecycle
  (start [self]
    (log/info "-> starting XrayChecker")
    (let [new-self (assoc self
                     :xray-config {:refresh-frequency           (props/parse-refresh-frequency config which-checker)
                                   :nr-checks-displayed         (props/parse-nr-checks-displayed config which-checker)
                                   :max-check-history           (props/parse-max-check-history config which-checker)
                                   :endpoint                    (props/parse-endpoint config which-checker)
                                   :environments                (props/parse-check-environments config which-checker)
                                   :acknowledge-hours-to-expire (props/parse-hours-to-expire config which-checker)}
                     :alerting-fn (atom nil)
                     :last-check (atom nil)
                     :registered-checks (atom {})
                     :check-results (atom {})
                     :acknowledged-checks (atom {}))
          frequency (get-in new-self [:xray-config :refresh-frequency])]
      (hndl/register-handler handler (xray-routes new-self))
      (log/info "this is your xray-config:  " (:xray-config new-self))
      (when frequency
        (at/every frequency (partial start-checks new-self) (sched/pool scheduler) :initial-delay 1000 :desc "Xray-Checker"))
      new-self))

  (stop [self]
    (log/info "<- stopping XrayChecker")
    self)

  XRayCheckerProtocol
  (set-alerting-function [{:keys [alerting-fn]} new-alerting-fn]
    (reset! alerting-fn new-alerting-fn))

  (register-check [self check check-id]
    (register-check-with-strategy self check (cleanup-id check-id) check-id default-strategy))
  (register-check [self check check-id title]
    (register-check-with-strategy self check (cleanup-id check-id) title default-strategy))

  (register-check-with-strategy [self check check-id strategy]
    (register-check-with-strategy self check check-id check-id strategy))
  (register-check-with-strategy [self check check-id title strategy]
    (log/info "registering check with id: " check-id)
    (let [cleaned-id (cleanup-id check-id)]
      (swap! (:registered-checks self) assoc cleaned-id (chk/->RegisteredXRayCheck check cleaned-id title strategy)))))

(defn new-xraychecker [which-checker]
  (map->XrayChecker {:which-checker which-checker}))
