(ns de.otto.tesla.xray.xray-checker
  (:require [com.stuartsierra.component :as c]
            [overtone.at-at :as at]
            [clojure.tools.logging :as log]
            [compojure.core :as comp]
            [clojure.string :as cs]
            [de.otto.tesla.xray.ui.detail-page :as dp]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.ui.overall-status :as oas]
            [de.otto.tesla.xray.conf.reading-properties :as props]
            [compojure.route :as croute]
            [de.otto.tesla.stateful.scheduler :as sched]
            [compojure.handler :as chandler]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]
            [clojure.data.json :as json]
            [clj-time.format :as tformat]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.stateful.handler :as hndl]
            [de.otto.tesla.xray.check :as chk]
            [clojure.data.xml :as xml])
  (:import (de.otto.tesla.xray.check XRayCheckResult)
           (org.joda.time.format DateTimeFormat)
           (org.joda.time DateTime)))

(defprotocol XRayCheckerProtocol
  (set-alerting-function [self alerting-fn])
  (register-check [self check check-id] [self check check-id title])
  (register-check-with-strategy [self check check-id strategy] [self check check-id title strategy]))

(defrecord RegisteredXRayCheck [check check-id title strategy]
  chk/XRayCheck
  (start-check [_ env]
    (chk/start-check check env)))

(defn send-alerts! [alerting-function check-id current-env results overall-status]
  (try
    (alerting-function {:last-result    (first results)
                        :overall-status overall-status
                        :check-id     check-id
                        :env            current-env})
    (catch Exception e
      (log/error e "Error when calling alerting function"))))

(defn do-alerting! [alerting-fn check-results check-id current-env overall-status]
  (when-let [alerting-function @alerting-fn]
    (let [results (get-in @check-results [check-id current-env :results])]
      (send-alerts! alerting-function check-id current-env results overall-status))))

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

(defn- update-results! [{:keys [alerting-fn check-results xray-config acknowledged-checks]} {:keys [check-id strategy]} current-env result]
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
    (when (or
            (existing-status-has-changed? overall-status new-overall-status)
            (initial-status-is-failure? overall-status new-overall-status))
      (do-alerting! alerting-fn check-results check-id current-env new-overall-status))))

(defn- check-result [xray-check current-env]
  (try
    (or
      (chk/start-check xray-check current-env)
      (chk/->XRayCheckResult :warning "no xray-result returned by check"))
    (catch Throwable t
      (log/info t "Exception thrown in check " (:check-id xray-check))
      (chk/->XRayCheckResult :error (.getMessage t)))))

(defn- check-result-with-timings [[^RegisteredXRayCheck xray-check current-env]]
  (let [start-time (utils/current-time)
        check-result (check-result xray-check current-env)
        stop-time (utils/current-time)]
    (chk/with-timings check-result (- stop-time start-time) stop-time)))

(defn- build-check-id-env-vecs [environments registered-checks]
  (for [check (vals registered-checks)
        environment environments]
    [check environment]))

(defn- timeout-response [check-id timeout]
  (chk/->XRayCheckResult :error (str check-id " did not finish in " timeout " ms") timeout (utils/current-time)))

(defn- entry-with-started-future [timeout check+env]
  (let [check-id (:check-id (first check+env))
        fallback (timeout-response check-id timeout)
        started-check-future (future (utils/execute-with-timeout timeout fallback (check-result-with-timings check+env)))]
    [check+env started-check-future]))

(defn- build-future-map [xray-config checks+env]
  (let [timeout (:refresh-frequency xray-config)
        map-entries (map (partial entry-with-started-future timeout) checks+env)]
    (into {} map-entries)))

(defn acknowledgement-active? [[_ end-time]]
  (> end-time (utils/current-time)))

(defn with-cleared-acknowledgement-entry [new-state [check-id env-acknowledgements]]
  (if-let [filtered (seq (filter acknowledgement-active? env-acknowledgements))]
    (assoc new-state check-id (into {} filtered))
    new-state))

(defn clear-outdated-acknowledgements! [{:keys [acknowledged-checks]}]
  (swap! acknowledged-checks #(reduce with-cleared-acknowledgement-entry {} %)))

(defn- start-the-xraychecks [{:keys [last-check registered-checks xray-config] :as self}]
  (try
    (clear-outdated-acknowledgements! self)
    (let [checks+env (build-check-id-env-vecs (:environments xray-config) @registered-checks)
          checks+env-to-futures (build-future-map xray-config checks+env)]
      (doseq [[[^RegisteredXRayCheck xray-check current-env] f] checks+env-to-futures]
        (update-results! self xray-check current-env (deref f)))
      (reset! last-check (utils/current-time)))
    (catch Exception e
      (log/error e "caught error when trying to start the xraychecks"))))

(defn acknowledge-check! [check-results acknowledged-checks check-id environment duration-in-hours]
  (let [duration-in-ms (* 60 60 1000 (Long/parseLong duration-in-hours))]
    (swap! acknowledged-checks assoc-in [check-id environment] (+ duration-in-ms (utils/current-time)))
    (swap! check-results assoc-in [check-id environment :overall-status] :acknowledged)))

(defn remove-acknowledgement! [acknowledged-checks check-id environment]
  (swap! acknowledged-checks update check-id dissoc environment)
  (swap! acknowledged-checks (fn [x] (into {} (filter #(not-empty (second %)) x)))))

(defn as-date-time [millis]
  (DateTime. millis))

(defn as-readable-time [millis]
  (.toString (as-date-time millis) (DateTimeFormat/forPattern "d MMMM, hh:mm")))

(defn stringify-acknowledged-checks [acknowledged-checks]
  (let [format-time (fn [_ value]
                      (if (number? value)
                        (as-readable-time value)
                        value))]
    (json/write-str @acknowledged-checks :value-fn format-time)))

(defn render-results-xml [check-results]
  (for [[check-id env-to-data] @check-results]
    (for [[env {results :results overall-status :overall-status}] env-to-data]
      (let [^XRayCheckResult result (first results)
            date-time-string (tformat/unparse (tformat/formatters :date-time) (DateTime. (:stop-time result)))]
        (xml/element :Project {:name            (str check-id " on " env)
                               :last-build-time date-time-string
                               :lastBuildStatus (str overall-status)} [])))))

(defn render-xml [check-results]
  (->> (render-results-xml check-results)
       (xml/element :Projects {})
       (xml/emit-str)))

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

        (comp/GET (str endpoint "/detail/:check-id/:environment") [check-id environment]
          {:status  200
           :headers {"Content-Type" "text/html"}
           :body    (dp/render-detail-page check-results acknowledged-checks xray-config check-id environment)})

        (comp/GET (str endpoint "/acknowledged-checks") []
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    (stringify-acknowledged-checks acknowledged-checks)})

        (comp/POST (str endpoint "/acknowledged-checks") [check-id environment hours]
          (acknowledge-check! check-results acknowledged-checks check-id environment hours)
          {:status  204
           :headers {"Content-Type" "text/plain"}
           :body    ""})

        (comp/DELETE (str endpoint "/acknowledged-checks/:check-id/:environment") [check-id environment]
          (remove-acknowledgement! acknowledged-checks check-id environment)
          {:status  204
           :headers {"Content-Type" "text/plain"}
           :body    ""})
        (comp/GET "/cc.xml" []
          {:status  200
           :headers {"Content-Type" "text/xml"}
           :body    (render-xml check-results)})
        ))))

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
        (at/every frequency (partial start-the-xraychecks new-self) (sched/pool scheduler) :desc "Xray-Checker"))
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
      (swap! (:registered-checks self) assoc cleaned-id (->RegisteredXRayCheck check cleaned-id title strategy)))))

(defn new-xraychecker [which-checker]
  (map->XrayChecker {:which-checker which-checker}))
