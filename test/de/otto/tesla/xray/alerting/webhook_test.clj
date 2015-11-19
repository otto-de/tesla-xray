(ns de.otto.tesla.xray.alerting.webhook-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.alerting.webhook :as webh]
    [de.otto.tesla.xray.check :as chk]
    [de.otto.tesla.system :as tesla]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [com.stuartsierra.component :as c]
    [com.stuartsierra.component :as comp]))

(defrecord ErrorCheck [should-fail?]
  chk/XRayCheck
  (start-check [_ _]
    (if @should-fail?
      (chk/->XRayCheckResult :error "error-message")
      (chk/->XRayCheckResult :ok "ok-message"))))

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest checks-and-check-results
  (testing "should register, check and store results"
    (let [should-fail? (atom false)
          webhook-alerts-send (atom [])]
      (with-redefs [webh/send-webhook-message (fn [_ msg] (swap! webhook-alerts-send conj msg))
                    chkr/current-time (fn [] 10)]
        (let [started (comp/start (test-system {:test-check-frequency            "100"
                                                :test-check-environments         "dev"
                                                :test-incoming-webhook-url "https://a-valid-url.to.start.alerting"
                                                :test-max-check-history          "2"}))
              xray-checker (:xray-checker started)]
          (chkr/register-check xray-checker (->ErrorCheck should-fail?) "DummyCheckA")
          (try
            (is (= {"DummyCheckA" (chkr/->RegisteredXRayCheck (->ErrorCheck should-fail?) "DummyCheckA" chkr/default-strategy)}
                   @(:checks xray-checker)))
            (Thread/sleep 100)
            (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                          :results        [(chk/->XRayCheckResult :ok "ok-message" 0 10)]}}}
                   @(:check-results xray-checker)))
            (is (= [] @webhook-alerts-send))
            (reset! should-fail? true)
            (Thread/sleep 100)
            (is (= {"DummyCheckA" {"dev" {:overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "error-message" 0 10)
                                                           (chk/->XRayCheckResult :ok "ok-message" 0 10)]}}}
                   @(:check-results xray-checker)))
            (is (= ["error-message"] @webhook-alerts-send))
            (Thread/sleep 100)
            (is (= {"DummyCheckA" {"dev" {:overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "error-message" 0 10)
                                                           (chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                   @(:check-results xray-checker)))
            (is (= ["error-message" "error-message"] @webhook-alerts-send))
            (finally
              (comp/stop started))))))))
