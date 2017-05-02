(ns de.otto.tesla.xray.alerting-test
  (:require
    [clojure.test :refer :all]
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
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config :scheduler]))))

(defn replace-all-timestamps [k]
  (if (:last-alert k)
    (assoc k :last-alert "LAST_ALERT")
    (if (instance? XRayCheckResult k)
      (assoc k :time-taken nil :stop-time nil)
      k)))

(defn without-timings [the-map]
  (walk/prewalk replace-all-timestamps the-map))

(def start-checks #'chkr/start-checks)

(deftest checks-and-check-results
  (let [should-fail? (atom false)
        alerts-send (atom [])]
    (let [started (comp/start (test-system {:test-check-frequency        "999999"
                                            :test-check-environments     "dev"
                                            :test-max-check-history      "3"}))
          xray-checker (:xray-checker started)]
      (chkr/set-alerting-function xray-checker (fn [{:keys [last-result]}] (swap! alerts-send conj (:message last-result))))
      (try
        (testing "should register the check"
          (chkr/register-check xray-checker (->ErrorCheck should-fail?) "DummyCheckA" "Checking A")
          (is (= {"DummyCheckA" (chk/->RegisteredXRayCheck (->ErrorCheck should-fail?) "DummyCheckA" "Checking A" chkr/default-strategy)}
                 @(:registered-checks xray-checker))))

        (testing "should execute the first run without alerting"
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "ok-message")]}}}
                 (without-timings @(:check-results xray-checker))))
          (is (= [] @alerts-send)))

        (testing "should execute the second run with first alert"
          (reset! should-fail? true)
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :error
                                        :results        [(chk/->XRayCheckResult :error "error-message")
                                                         (chk/->XRayCheckResult :ok "ok-message")]}}}
                 (without-timings @(:check-results xray-checker))))
          (is (= ["error-message"] @alerts-send)))

        (testing "should execute the third run without alert"
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :error
                                        :results        [(chk/->XRayCheckResult :error "error-message")
                                                         (chk/->XRayCheckResult :error "error-message")
                                                         (chk/->XRayCheckResult :ok "ok-message")]}}}
                 (without-timings @(:check-results xray-checker))))
          (is (= ["error-message"] @alerts-send)))

        (testing "should execute the fourth run with alert"
          (reset! should-fail? false)
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "ok-message")
                                                         (chk/->XRayCheckResult :error "error-message")
                                                         (chk/->XRayCheckResult :error "error-message")]}}}
                 (without-timings @(:check-results xray-checker))))
          (is (= ["error-message" "ok-message"] @alerts-send)))
        (finally
          (comp/stop started))))))


(deftest check-nr-of-alerts
  (let [should-fail? (atom false)
        alerts-send (atom [])]
    (let [started (comp/start (test-system {:test-check-frequency        "999999"
                                            :test-check-environments     "dev;test;bar;baz"
                                            :test-alerting-schedule-time "100"
                                            :test-max-check-history      "3"}))
          xray-checker (:xray-checker started)]
      (chkr/set-alerting-function xray-checker (fn [{:keys [last-result]}] (swap! alerts-send conj (:message last-result))))
      (try
        (testing "should register the check"
          (chkr/register-check xray-checker (->ErrorCheck should-fail?) "DummyCheckA")
          (reset! should-fail? true)
          (start-checks xray-checker)
          (is (= 4 (count @alerts-send)))
          (is (= ["error-message"
                  "error-message"
                  "error-message"
                  "error-message"] @alerts-send)))
        (finally
          (comp/stop started))))))
