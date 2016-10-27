(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]
    [com.stuartsierra.component :as comp]
    [de.otto.tesla.system :as tesla]
    [com.stuartsierra.component :as c]
    [de.otto.tesla.xray.util.utils :as utils]
    [de.otto.tesla.stateful.handler :as handler]
    [ring.mock.request :as mock]))

(defrecord DummyCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok "dummy-message")))

(defrecord ErrorCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :error "error-message")))

(defrecord BlockingCheck []
  chk/XRayCheck
  (start-check [_ _]
    (while true
      (Thread/sleep 1000))))

(defrecord DummyCheckWithoutReturnedStatus []
  chk/XRayCheck
  (start-check [_ _]))

(defrecord AssertionCheck []
  chk/XRayCheck
  (start-check [_ _]
    (assert (= 1 2))))

(defrecord WaitingDummyCheck [t]
  chk/XRayCheck
  (start-check [_ _]
    (Thread/sleep t)
    (chk/->XRayCheckResult :ok t)))

(defrecord FailingCheck []
  chk/XRayCheck
  (start-check [_ _]
    (throw (RuntimeException. "failing message"))))

(def start-the-xraychecks #'chkr/start-the-xraychecks)

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest check-scheduling
  (testing "should execute checks with configured check-frequency"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckA")
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckB")
        (try
          (is (= {"DummyCheckA" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckA" chkr/default-strategy)
                  "DummyCheckB" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckB" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker))))))))


(deftest should-handle-blocking-checks
  (testing "should timeout blocking-checks before they run again"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->BlockingCheck) "BlockingCheck")
        (try
          (is (= {"BlockingCheck" (chkr/->RegisteredXRayCheck (->BlockingCheck) "BlockingCheck" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (Thread/sleep 310)
          (is (= {"BlockingCheck" {"dev" {:overall-status :error
                                          :results        [(chk/->XRayCheckResult :error "BlockingCheck did not finish in 100 ms" 100 10)
                                                           (chk/->XRayCheckResult :error "BlockingCheck did not finish in 100 ms" 100 10)]}}}
                 @(:check-results xray-checker))))))))

(deftest checks-and-check-results
  (testing "should register, check and store results"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckA")
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckB")
        (try
          (is (= {"DummyCheckA" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckA" chkr/default-strategy)
                  "DummyCheckB" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckB" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (start-the-xraychecks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (start-the-xraychecks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (start-the-xraychecks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (finally
            (comp/stop started))))))

  (testing "should apply the correct state to checks that are in the acknowledged atom"
    (let [started (comp/start (test-system {:test-check-frequency    "100"
                                            :test-check-environments "dev"
                                            :test-max-check-history  "2"}))
          xray-checker (:xray-checker started)]
      (chkr/register-check xray-checker (->ErrorCheck) "ErrorCheck")
      (try
        (with-redefs [utils/current-time (fn [] 10)]
          (is (= {}
                 @(:acknowledged-checks xray-checker)))
          (start-the-xraychecks xray-checker)
          (is (= {"ErrorCheck" {"dev" {:overall-status :error
                                       :results        [(chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (swap! (:acknowledged-checks xray-checker) assoc "ErrorCheck" 15)
          (start-the-xraychecks xray-checker)
          (is (= {"ErrorCheck" {"dev" {:overall-status :acknowledged
                                       :results        [(chk/->XRayCheckResult :error "error-message" 0 10)
                                                        (chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                 @(:check-results xray-checker))))

        (with-redefs [utils/current-time (fn [] 20)]
          (start-the-xraychecks xray-checker)
          (is (= {}
                 @(:acknowledged-checks xray-checker)))
          (is (= {"ErrorCheck" {"dev" {:overall-status :error
                                       :results        [(chk/->XRayCheckResult :error "error-message" 0 20)
                                                        (chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                 @(:check-results xray-checker))))
        (finally
          (comp/stop started))))))

(deftest error-handling
  (testing "should store warning-result for check without a propper return message"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            xray-checker (:xray-checker started)]
        (try
          (chkr/register-check xray-checker (->DummyCheckWithoutReturnedStatus) "DummyCheckWithoutReturnedStatus")
          (start-the-xraychecks xray-checker)
          (Thread/sleep 10)
          (is (= {"DummyCheckWithoutReturnedStatus" {"dev" {:overall-status :warning
                                                            :results        [(chk/->XRayCheckResult :warning "no xray-result returned by check" 0 10)]}}}
                 @(:check-results xray-checker)))
          (finally
            (comp/stop started))))))
  (testing "should be able to catch assertions in test"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            xray-checker (:xray-checker started)]
        (try
          (chkr/register-check xray-checker (->AssertionCheck) "AssertionCheck")
          (start-the-xraychecks xray-checker)
          (Thread/sleep 10)
          (is (= {"AssertionCheck" {"dev" {:overall-status :error
                                           :results        [(chk/->XRayCheckResult :error "Assert failed: (= 1 2)" 0 10)]}}}
                 @(:check-results xray-checker)))
          (finally
            (comp/stop started)))))))

(deftest request-handling-and-html-responses
  (with-redefs [utils/current-time (fn [] 1447152024778)]
    (let [started (comp/start (test-system {:test-check-frequency    "100"
                                            :test-check-environments "dev"}))
          xray-checker (:xray-checker started)
          handlers (handler/handler (:handler started))]
      (try
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheck")
        (start-the-xraychecks xray-checker)

        (testing "should visualize the response on overview-page"
          (let [response (handlers (mock/request :get "/xray-checker/overview"))]
            (is (= 200 (:status response)))
            (is (= {"Content-Type" "text/html"} (:headers response)))
            (is (= true (.contains (:body response) "2015.11.10 11:40:24 tt:0 dummy-message")))))

        (testing "should visualize the overall-status on root-page"
          (let [response (handlers (mock/request :get "/xray-checker"))]
            (is (= 200 (:status response)))
            (is (= {"Content-Type" "text/html"} (:headers response)))
            (is (= true (.contains (:body response) "ok")))))

        (testing "should visualize the detail-status on detail-page"
          (let [response (handlers (mock/request :get "/xray-checker/detail/DummyCheck/dev"))]
            (is (= 200 (:status response)))
            (is (= {"Content-Type" "text/html"} (:headers response)))
            (is (= true (.contains (:body response) "dev")))
            (is (= true (.contains (:body response) "2015.11.10 11:40:24 tt:0 dummy-message")))))
        (finally
          (comp/stop started))))))

(deftest execution-in-parallel
  (testing "should execute checks in parallel"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "9999999"
                                              :test-check-environments "dev"}))
            xray-checker (:xray-checker started)]
        (try
          (chkr/register-check xray-checker (->WaitingDummyCheck 0) "DummyCheck1")
          (chkr/register-check xray-checker (->WaitingDummyCheck 100) "DummyCheck2")
          (chkr/register-check xray-checker (->WaitingDummyCheck 100) "DummyCheck3")
          (start-the-xraychecks xray-checker)               ; wait for start
          (Thread/sleep 100)
          (is (= {"DummyCheck1" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok 0 0 10)]}}
                  "DummyCheck2" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok 100 0 10)]}}
                  "DummyCheck3" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok 100 0 10)]}}}
                 @(:check-results xray-checker)))
          (finally
            (comp/stop started)))))))

(deftest acknowledge-check-endpoints
  (testing "should put a check object with correct expire time in the acknowledged-checks atom"
    (with-redefs [utils/current-time (constantly 10)]
      (let [acknowledged-checks (atom {})]
        (chkr/acknowledge-check! acknowledged-checks "oneHourAcknowledgement" "60")
        (is (= ["oneHourAcknowledgement" (+ 10 (* 60 60 1000))] (first @acknowledged-checks))))))
  (testing "should work for long expiry times"
    (with-redefs [utils/current-time (constantly 10)]
      (let [acknowledged-checks (atom {})]
        (chkr/acknowledge-check! acknowledged-checks "oneYearAcknowledgement" (str (* 60 24 365)))
        (is (= ["oneYearAcknowledgement" (+ 10 (* 365 24 60 60 1000))] (first @acknowledged-checks))))))

  (testing "should remove a check object regardless of it's expire time"
    (let [acknowledged-checks (atom {"checkToBeRemoved" 1000})]
      (chkr/remove-acknowledgement! acknowledged-checks "checkToBeRemoved")
      (is (= {} @acknowledged-checks))))

  (testing "should stringify an empty acknowledgement map"
    (let [acknowledged-checks (atom {})]
      (is (= "{}" (chkr/stringify-acknowledged-checks acknowledged-checks)))))

  (testing "should stringify acknowledgement map with one check"
    (let [acknowledged-checks (atom {"firstCheck" 100})]
      (is (= "{\"firstCheck\":100}" (chkr/stringify-acknowledged-checks acknowledged-checks)))))

  (testing "should stringify acknowledgement map with two checks"
    (let [acknowledged-checks (atom {"firstCheck" 100 "secondCheck" 500})]
      (is (= "{\"firstCheck\":100,\"secondCheck\":500}" (chkr/stringify-acknowledged-checks acknowledged-checks))))))

(def build-check-name-env-vecs #'chkr/build-check-name-env-vecs)
(deftest building-parameters-for-futures
  (testing "should build a propper parameter vector for all checks"
    (let [check-a (chkr/->RegisteredXRayCheck "A" "A" "A")
          check-b (chkr/->RegisteredXRayCheck "B" "B" "B")]
      (is (= [[check-a "dev"] [check-a "test"]
              [check-b "dev"] [check-b "test"]]
             (build-check-name-env-vecs ["dev" "test"] {"CheckA" check-a
                                                        "CheckB" check-b}))))))
