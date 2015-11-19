(ns de.otto.tesla.xray.alerting.webhook-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.alerting.webhook :as webh]
    [de.otto.tesla.xray.check :as chk]
    [de.otto.tesla.system :as tesla]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [com.stuartsierra.component :as c]
    [clojure.walk :as walk]
    [com.stuartsierra.component :as comp])
  (:import (de.otto.tesla.xray.check XRayCheckResult)))

(defrecord ErrorCheck [should-fail?]
  chk/XRayCheck
  (start-check [_ _]
    (if @should-fail?
      (chk/->XRayCheckResult :error "error-message")
      (chk/->XRayCheckResult :ok "ok-message"))))

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(defn replace-all-timestamps [k]
  (if (:last-alert k)
    (assoc k :last-alert "LAST_ALERT")
    (if (instance? XRayCheckResult k)
      (assoc k :time-taken nil :stop-time nil)
      k)))

(defn without-timings [the-map]
  (walk/prewalk replace-all-timestamps the-map))

(def start-the-xraychecks #'chkr/start-the-xraychecks)

(deftest checks-and-check-results
  (let [should-fail? (atom false)
        webhook-alerts-send (atom [])]
    (with-redefs [webh/send-webhook-message! (fn [_ msg] (swap! webhook-alerts-send conj msg))]
      (let [started (comp/start (test-system {:test-check-frequency        ""
                                              :test-check-environments     "dev"
                                              :test-alerting-schedule-time "100"
                                              :test-incoming-webhook-url   "https://a-valid-url.to.start.alerting"
                                              :test-max-check-history      "3"}))
            xray-checker (:xray-checker started)]
        (try
          (testing "should register the check"
            (chkr/register-check xray-checker (->ErrorCheck should-fail?) "DummyCheckA")
            (is (= {"DummyCheckA" (chkr/->RegisteredXRayCheck (->ErrorCheck should-fail?) "DummyCheckA" chkr/default-strategy)}
                   @(:checks xray-checker))))

          (testing "should execute the first run without alerting"
            (start-the-xraychecks xray-checker)
            (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                          :results        [(chk/->XRayCheckResult :ok "ok-message")]}}}
                   (without-timings @(:check-results xray-checker))))
            (is (= [] @webhook-alerts-send)))

          (testing "should execute the second run with first alert"
            (reset! should-fail? true)
            (start-the-xraychecks xray-checker)
            (is (= {"DummyCheckA" {"dev" {:last-alert     "LAST_ALERT"
                                          :overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "error-message")
                                                           (chk/->XRayCheckResult :ok "ok-message")]}}}
                   (without-timings @(:check-results xray-checker))))
            (is (= ["DummyCheckA failed on dev with message: error-message"] @webhook-alerts-send)))

          (testing "should execute the third run without alert"
            (start-the-xraychecks xray-checker)
            (is (= {"DummyCheckA" {"dev" {:last-alert     "LAST_ALERT"
                                          :overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "error-message")
                                                           (chk/->XRayCheckResult :error "error-message")
                                                           (chk/->XRayCheckResult :ok "ok-message")]}}}
                   (without-timings @(:check-results xray-checker))))
            (is (= ["DummyCheckA failed on dev with message: error-message"] @webhook-alerts-send)))

          (testing "should execute the fourth run with alert"
            (Thread/sleep 100); wait for alerting schedule time
            (start-the-xraychecks xray-checker)
            (is (= {"DummyCheckA" {"dev" {:last-alert     "LAST_ALERT"
                                          :overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "error-message")
                                                           (chk/->XRayCheckResult :error "error-message")
                                                           (chk/->XRayCheckResult :error "error-message")]}}}
                   (without-timings @(:check-results xray-checker))))
            (is (= ["DummyCheckA failed on dev with message: error-message" "DummyCheckA failed on dev with message: error-message"] @webhook-alerts-send)))
        (finally
          (comp/stop started)))))) )


(deftest check-nr-of-alerts
  (let [should-fail? (atom false)
        webhook-alerts-send (atom [])]
    (with-redefs [webh/send-webhook-message! (fn [_ msg] (swap! webhook-alerts-send conj msg))]
      (let [started (comp/start (test-system {:test-check-frequency        ""
                                              :test-check-environments     "dev;test;bar;baz"
                                              :test-alerting-schedule-time "100"
                                              :test-incoming-webhook-url   "https://a-valid-url.to.start.alerting"
                                              :test-max-check-history      "3"}))
            xray-checker (:xray-checker started)]
        (try
          (testing "should register the check"
            (chkr/register-check xray-checker (->ErrorCheck should-fail?) "DummyCheckA")
            (reset! should-fail? true)
            (start-the-xraychecks xray-checker)
            (is (= 4 (count @webhook-alerts-send)))
            (is (= #{"DummyCheckA failed on baz with message: error-message"
                     "DummyCheckA failed on test with message: error-message"
                     "DummyCheckA failed on dev with message: error-message"
                     "DummyCheckA failed on bar with message: error-message"} (into #{} @webhook-alerts-send))))
          (finally
            (comp/stop started)))))))
