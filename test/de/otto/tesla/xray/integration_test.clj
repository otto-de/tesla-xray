(ns de.otto.tesla.xray.integration-test
  (:require
    [com.stuartsierra.component :as comp]
    [de.otto.tesla.system :as tesla]
    [clojure.test :refer :all]
    [com.stuartsierra.component :as c]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]
    [ring.mock.request :as mock]
    [de.otto.tesla.stateful.handler :as handler]))

(defrecord DummyCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok "dummy-message")))

(defrecord FailingCheck []
  chk/XRayCheck
  (start-check [_ _]
    (throw (RuntimeException. "failing message"))))

(def start-the-xraychecks #'chkr/start-the-xraychecks)

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest registering-and-storing-results
  (testing "should register and check and store its results"
    (with-redefs [chkr/current-time (fn [] 10)]
      (let [started (comp/start (test-system {:test-check-frequency    "100"
                                              :test-check-environments "dev"}))
            rt-checker (:rt-checker started)]
        (try
          (chkr/register-check rt-checker (->DummyCheck) "DummyCheck")
          (is (= ["DummyCheck"] (keys @(:checks rt-checker))))
          (is (= DummyCheck (class (:check (first (vals @(:checks rt-checker)))))))
          (start-the-xraychecks rt-checker)
          (is (= {"DummyCheck" {"dev" [(chk/->XRayCheckResult :ok "dummy-message" 0 10)]}}
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
        (start-the-xraychecks rt-checker)
        (is (= {"FailingCheck" {"dev" [(chk/->XRayCheckResult :error "failing message")]}}
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
