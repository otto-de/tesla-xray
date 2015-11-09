(ns de.otto.tesla.xray.integration-test
  (:require
    [com.stuartsierra.component :as comp]
    [de.otto.tesla.system :as tesla]
    [clojure.test :refer :all]
    [com.stuartsierra.component :as c]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]))

(defrecord DummyCheck []
  chk/XRayCheck
  (start-realtime-check [_ _]
    (chk/->XRayCheckResult :ok "dummyresponse" 123)))

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest registering-and-storing-results
  (testing "should register and check and store its results"
    (let [started (comp/start (test-system {:test-check-frequency    "100"
                                            :test-check-environments "dev"}))
          rt-checker (:rt-checker started)]
      (try
        (chkr/register-realtime-check rt-checker (->DummyCheck) "DummyCheck")
        (is (= ["DummyCheck"] (keys @(:checks rt-checker))))
        (is (= DummyCheck (class (first (vals @(:checks rt-checker))))))
        (chkr/start-the-realtimechecks rt-checker)
        (is (= {"DummyCheck" {"dev" [(chk/->XRayCheckResult :ok "dummyresponse" 123)]}}
               @(:check-results rt-checker)))
        (finally
          (comp/stop started))))))
