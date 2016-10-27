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
            [compojure.handler :as chandler]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]
            [clojure.data.json :as json]))

(defprotocol XRayCheckerProtocol
  (set-alerting-function [self alerting-fn])
  (register-check [self check checkname])
  (register-check-with-strategy [self check checkname strategy]))

(defrecord RegisteredXRayCheck [check check-name strategy]
  chk/XRayCheck
  (start-check [_ env]
    (chk/start-check check env)))

(defn send-alerts! [alerting-function check-name current-env results overall-status]
  (try
    (alerting-function {:last-result    (first results)
                        :overall-status overall-status
                        :check-name     check-name
                        :env            current-env})
    (catch Exception e
      (log/error e "Error when calling alerting function"))))

(defn do-alerting! [alerting-fn check-results check-name current-env overall-status]
  (when-let [alerting-function @alerting-fn]
    (let [results (get-in @check-results [check-name current-env :results])]
      (send-alerts! alerting-function check-name current-env results overall-status))))

(defn- append-result [old-results result max-check-history]
  (let [limited-results (take (dec max-check-history) old-results)]
    (conj limited-results result)))

(defn existing-status-has-changed? [overall-status new-overall-status]
  (if overall-status
    (not= overall-status new-overall-status)))

(defn initial-status-is-failure? [overall-status new-overall-status]
  (and
    (nil? overall-status)
    (not= :ok new-overall-status)))

(defn- update-results! [{:keys [alerting-fn check-results xray-config acknowledged-checks]} {:keys [check-name strategy]} current-env result]
  (let [{:keys [max-check-history]} xray-config
        {:keys [results overall-status]} (get-in @check-results [check-name current-env])
        new-results (append-result results result max-check-history)
        new-overall-status (if (contains? @acknowledged-checks check-name)
                             :acknowledged
                             (strategy new-results))]
    (swap! check-results assoc-in [check-name current-env :results] new-results)
    (swap! check-results assoc-in [check-name current-env :overall-status] new-overall-status)
    (when (or
            (existing-status-has-changed? overall-status new-overall-status)
            (initial-status-is-failure? overall-status new-overall-status))
      (do-alerting! alerting-fn check-results check-name current-env new-overall-status))))

(defn- check-result [xray-check current-env]
  (try
    (or
      (chk/start-check xray-check current-env)
      (chk/->XRayCheckResult :warning "no xray-result returned by check"))
    (catch Throwable t
      (log/info t "Exception thrown in check " (:check-name xray-check))
      (chk/->XRayCheckResult :error (.getMessage t)))))

(defn- check-result-with-timings [[^RegisteredXRayCheck xray-check current-env]]
  (let [start-time (utils/current-time)
        check-result (check-result xray-check current-env)
        stop-time (utils/current-time)]
    (chk/with-timings check-result (- stop-time start-time) stop-time)))

(defn- build-check-name-env-vecs [environments registered-checks]
  (for [check (vals registered-checks)
        environment environments]
    [check environment]))

(defn- timeout-response [check-name timeout]
  (chk/->XRayCheckResult :error (str check-name " did not finish in " timeout " ms") timeout (utils/current-time)))

(defn- entry-with-started-future [timeout check+env]
  (let [check-name (:check-name (first check+env))
        fallback (timeout-response check-name timeout)
        started-check-future (future (utils/execute-with-timeout timeout fallback (check-result-with-timings check+env)))]
    [check+env started-check-future]))

(defn- build-future-map [xray-config checks+env]
  (let [timeout (:refresh-frequency xray-config)
        map-entries (map (partial entry-with-started-future timeout) checks+env)]
    (into {} map-entries)))



(defn clear-outdated-acknowledgements! [{:keys [acknowledged-checks]}]
  (swap! acknowledged-checks (fn [acknowledged-checks] (->>
                                                         acknowledged-checks
                                                         (filter #(> (second %) (utils/current-time)))
                                                         (into {})))))

(defn- start-the-xraychecks [{:keys [last-check registered-checks xray-config] :as self}]
  (clear-outdated-acknowledgements! self)
  (let [checks+env (build-check-name-env-vecs (:environments xray-config) @registered-checks)
        checks+env-to-futures (build-future-map xray-config checks+env)]
    (doseq [[[^RegisteredXRayCheck xray-check current-env] f] checks+env-to-futures]
      (update-results! self xray-check current-env (deref f)))
    (reset! last-check (utils/current-time))))

(defn acknowledge-check! [acknowledged-checks check-name duration-in-min]
  (let [duration-in-ms (* 60 1000 (Long/parseLong duration-in-min))]
    (swap! acknowledged-checks assoc check-name (+ duration-in-ms (utils/current-time))))
  (print @acknowledged-checks))

(defn remove-acknowledgement! [acknowledged-checks check-name]
  (swap! acknowledged-checks dissoc check-name))

(defn stringify-acknowledged-checks [acknowledged-checks]
  (json/write-str @acknowledged-checks))

(defn- xray-routes [{:keys [check-results last-check xray-config acknowledged-checks]}]
  (let [{:keys [endpoint]} xray-config]
    (chandler/api
      (comp/routes
        (croute/resources "/")
        (comp/GET endpoint []
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    (oas/render-overall-status check-results last-check xray-config)})

        (comp/GET (str endpoint "/overview") []
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    (eo/render-env-overview check-results last-check xray-config)})

        (comp/GET (str endpoint "/detail/:check-name/:environment") [check-name environment]
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    (dp/render-detail-page check-results xray-config check-name environment)})

        (comp/GET (str endpoint "/acknowledged-checks") []
          {:status  200
           :headers {"Content-Type" "text/plain"}
           :body    (stringify-acknowledged-checks acknowledged-checks)})

        (comp/POST (str endpoint "/acknowledged-checks") [check-name minutes]
          {:status  200
           :headers {"Content-Type" "text/plain"}
           :body    (acknowledge-check! acknowledged-checks check-name minutes)})

        (comp/DELETE (str endpoint "/acknowledged-checks/:check-name") [check-name]
          {:status  200
           :headers {"Content-Type" "text/plain"}
           :body    (remove-acknowledgement! acknowledged-checks check-name)})
        ))))


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
                                   :environments        (props/parse-check-environments config which-checker)}
                     :executor executor
                     :alerting-fn (atom nil)
                     :last-check (atom nil)
                     :registered-checks (atom {})
                     :check-results (atom {})
                     :acknowledged-checks (atom {}))
          frequency (get-in new-self [:xray-config :refresh-frequency])]
      (hndl/register-handler handler (xray-routes new-self))
      (log/info "this is your xray-config:  " (:xray-config new-self))
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
    (swap! (:registered-checks self) assoc checkname (->RegisteredXRayCheck check checkname strategy))))

(defn new-xraychecker [which-checker]
  (map->XrayChecker {:which-checker which-checker}))
