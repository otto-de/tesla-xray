(ns de.otto.tesla.xray.visualization-test
  (:require [clojure.test :refer :all]
            [de.otto.tesla.system :as tesla]
            [com.stuartsierra.component :as c]
            [ring.mock.request :as mock]
            [de.otto.tesla.stateful.handler :as handler]
            [com.stuartsierra.component :as comp]
            [de.otto.tesla.xray.xray-checker :as chkr]
            [de.otto.tesla.xray.check :as chk]))

(defrecord DummyCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok "dummyresponse" 123)))

(def start-the-xraychecks #'chkr/start-the-xraychecks)

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))))

(deftest registering-and-storing-results
         (testing "should register and check and store its results"
                  (let [started (comp/start (test-system {:test-check-frequency    "100"
                                                          :test-check-environments "dev"}))
                        rt-checker (:rt-checker started)
                        handlers (handler/handler (:handler started))]
                    (try
                      (chkr/register-realtime-check rt-checker (->DummyCheck) "DummyCheck")
                      (start-the-xraychecks rt-checker)
                      (let [response (handlers (mock/request :get "/rt-checker"))]
                        (is (= 200 (:status response)))
                        (is (= {"Content-Type" "text/html"} (:headers response)))
                        (is (= true (.contains (:body response) "dev"))))
                      (finally
                        (comp/stop started))))))

