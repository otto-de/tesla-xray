(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]
    [com.stuartsierra.component :as comp]
    [de.otto.tesla.system :as tesla]
    [de.otto.tesla.util.test-utils :refer [eventually]]
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

(def start-checks #'chkr/start-checks)

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config :scheduler]))))

(deftest check-scheduling
  (testing "should execute checks with configured check-frequency"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckA" "Checking A")
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckB" "Checking B")
        (try
          (is (= {"DummyCheckA" (chk/->RegisteredXRayCheck (->DummyCheck) "DummyCheckA" "Checking A" chkr/default-strategy)
                  "DummyCheckB" (chk/->RegisteredXRayCheck (->DummyCheck) "DummyCheckB" "Checking B" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (eventually (= {"DummyCheckA" {"dev" {:overall-status :ok
                                                :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                          "DummyCheckB" {"dev" {:overall-status :ok
                                                :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                         @(:check-results xray-checker)))
          (finally
            (comp/stop started)))
        ))))


(deftest should-handle-blocking-checks
  (testing "should timeout blocking-checks before they run again"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->BlockingCheck) "BlockingCheck" "Checking Blocking")
        (try
          (is (= {"BlockingCheck" (chk/->RegisteredXRayCheck (->BlockingCheck) "BlockingCheck" "Checking Blocking" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (eventually (= {"BlockingCheck" {"dev" {:overall-status :error
                                                  :results        [(chk/->XRayCheckResult :error "BlockingCheck did not finish in 100 ms" 100 10)
                                                                   (chk/->XRayCheckResult :error "BlockingCheck did not finish in 100 ms" 100 10)]}}}
                         @(:check-results xray-checker)))
          (finally
            (comp/stop started)))))))

(deftest checks-and-check-results
  (testing "should register, check and store results"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            xray-checker (:xray-checker started)]
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckA" "Checking A")
        (chkr/register-check xray-checker (->DummyCheck) "DummyCheckB" "Checking B")
        (try
          (is (= {"DummyCheckA" (chk/->RegisteredXRayCheck (->DummyCheck) "DummyCheckA" "Checking A" chkr/default-strategy)
                  "DummyCheckB" (chk/->RegisteredXRayCheck (->DummyCheck) "DummyCheckB" "Checking B" chkr/default-strategy)}
                 @(:registered-checks xray-checker)))
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (start-checks xray-checker)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results        [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                         (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results xray-checker)))
          (finally
            (comp/stop started)))))))



(deftest error-handling
  (testing "should store warning-result for check without a propper return message"
    (with-redefs [utils/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            xray-checker (:xray-checker started)]
        (try
          (chkr/register-check xray-checker (->DummyCheckWithoutReturnedStatus) "DummyCheckWithoutReturnedStatus")
          (eventually (= {"DummyCheckWithoutReturnedStatus" {"dev" {:overall-status :warning
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
          (eventually (= {"AssertionCheck" {"dev" {:overall-status :error
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
        (start-checks xray-checker)

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
        (testing "should emit cc xml for monitors"
          (let [response (handlers (mock/request :get "/cc.xml"))]
            (is (= 200 (:status response)))
            (is (= {"Content-Type" "text/xml"} (:headers response)))
            (is (= "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Projects><Project name=\"DummyCheck on dev\" last-build-time=\"2015-11-10T10:40:24.778Z\" lastBuildStatus=\":ok\"></Project></Projects>"
                   (:body response)))))

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
          (start-checks xray-checker)               ; wait for start
          (eventually (= {"DummyCheck1" {"dev" {:overall-status :ok
                                                :results        [(chk/->XRayCheckResult :ok 0 0 10)]}}
                          "DummyCheck2" {"dev" {:overall-status :ok
                                                :results        [(chk/->XRayCheckResult :ok 100 0 10)]}}
                          "DummyCheck3" {"dev" {:overall-status :ok
                                                :results        [(chk/->XRayCheckResult :ok 100 0 10)]}}}
                         @(:check-results xray-checker)))
          (finally
            (comp/stop started)))))))


(def build-check-id-env-vecs #'chkr/combine-each-check-and-env)
(deftest building-parameters-for-futures
  (testing "should build a propper parameter vector for all checks"
    (let [check-a (chk/->RegisteredXRayCheck "A" "A" "A" "A")
          check-b (chk/->RegisteredXRayCheck "B" "B" "B" "B")]
      (is (= [[check-a "dev"] [check-a "test"]
              [check-b "dev"] [check-b "test"]]
             (build-check-id-env-vecs ["dev" "test"] {"CheckA" check-a
                                                      "CheckB" check-b}))))))
