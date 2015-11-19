(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]
    [com.stuartsierra.component :as comp]
    [de.otto.tesla.system :as tesla]
    [com.stuartsierra.component :as c]
    [de.otto.tesla.stateful.handler :as handler]
    [ring.mock.request :as mock]))

(defrecord DummyCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok "dummy-message")))

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
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest checks-and-check-results
  (testing "should register, check and store results"
    (with-redefs [chkr/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"
                                              :test-max-check-history  "2"}))
            rt-checker (:rt-checker started)]
        (chkr/register-check rt-checker (->DummyCheck) "DummyCheckA")
        (chkr/register-check rt-checker (->DummyCheck) "DummyCheckB")
        (try
          (is (= {"DummyCheckA" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckA" chkr/default-strategy)
                  "DummyCheckB" (chkr/->RegisteredXRayCheck (->DummyCheck) "DummyCheckB" chkr/default-strategy)}
                 @(:checks rt-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results rt-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                  (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                  (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results rt-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheckA" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                  (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
                  "DummyCheckB" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok "dummy-message" 0 10)
                                                  (chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}}
                 @(:check-results rt-checker)))
          (finally
            (comp/stop started)))))))

(deftest error-handling
  (testing "should not stop even if exceptions occur"
    (let [started (comp/start (test-system {:test-check-frequency    "100"
                                            :test-check-environments "dev"}))
          rt-checker (:rt-checker started)]
      (try
        (chkr/register-check rt-checker (->FailingCheck) "FailingCheck")
        (Thread/sleep 150)
        (is (= {"FailingCheck" {"dev" {:overall-status :error
                                       :results [(chk/->XRayCheckResult :error "failing message")]}}}
               @(:check-results rt-checker)))
        (finally
          (comp/stop started))))))

(deftest request-handling-and-html-responses
  (with-redefs [chkr/current-time (fn [] 1447152024778)]
    (testing "should register, check, store and visualize results"
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            rt-checker (:rt-checker started)
            handlers (handler/handler (:handler started))]
        (try
          (chkr/register-check rt-checker (->DummyCheck) "DummyCheck")
          (start-the-xraychecks rt-checker)
          (let [response (handlers (mock/request :get "/rt-checker"))]
            (is (= 200 (:status response)))
            (is (= {"Content-Type" "text/html"} (:headers response)))
            (is (= true (.contains (:body response) "2015-11-10T10:40:24.778Z tt:0 dummy-message"))))
          (finally
            (comp/stop started)))))))

(deftest execution-in-parallel
  (testing "should execute checks in parallel"
    (with-redefs [chkr/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            rt-checker (:rt-checker started)]
        (try
          (chkr/register-check rt-checker (->WaitingDummyCheck 0) "DummyCheck1")
          (chkr/register-check rt-checker (->WaitingDummyCheck 100) "DummyCheck2")
          (chkr/register-check rt-checker (->WaitingDummyCheck 100) "DummyCheck3")
          (chkr/register-check rt-checker (->WaitingDummyCheck 200) "DummyCheck4")
          (chkr/register-check rt-checker (->WaitingDummyCheck 200) "DummyCheck5")
          (Thread/sleep 100)                                ; wait for start
          (is (= {"DummyCheck1" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 0 0 10)]}}}
                 @(:check-results rt-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheck1" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 0 0 10)
                                                  (chk/->XRayCheckResult :ok 0 0 10)]}}
                  "DummyCheck2" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 100 0 10)]}}
                  "DummyCheck3" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 100 0 10)]}}}
                 @(:check-results rt-checker)))
          (Thread/sleep 100)
          (is (= {"DummyCheck1" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 0 0 10)
                                                  (chk/->XRayCheckResult :ok 0 0 10)
                                                  (chk/->XRayCheckResult :ok 0 0 10)]}}
                  "DummyCheck2" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 100 0 10)
                                                  (chk/->XRayCheckResult :ok 100 0 10)]}}
                  "DummyCheck3" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 100 0 10)
                                                  (chk/->XRayCheckResult :ok 100 0 10)]}}
                  "DummyCheck4" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 200 0 10)]}}
                  "DummyCheck5" {"dev" {:overall-status :ok
                                        :results [(chk/->XRayCheckResult :ok 200 0 10)]}}}
                 @(:check-results rt-checker)))
          (finally
            (comp/stop started)))))))

(def build-check-name-env-vecs #'chkr/build-check-name-env-vecs)
(deftest building-parameters-for-futures
  (testing "should build a propper parameter vector for all checks"
    (is (= [[(chkr/->RegisteredXRayCheck "A" "A" "A") "dev"] [(chkr/->RegisteredXRayCheck "A" "A" "A") "test"]
            [(chkr/->RegisteredXRayCheck "B" "B" "B") "dev"] [(chkr/->RegisteredXRayCheck "B" "B" "B") "test"]]
           (build-check-name-env-vecs ["dev" "test"] (atom {"CheckA" (chkr/->RegisteredXRayCheck "A" "A" "A")
                                                            "CheckB" (chkr/->RegisteredXRayCheck "B" "B" "B")}))))))

