(ns de.otto.tesla.xray.acknowledge-test
  (:require [clojure.test :refer :all]
            [de.otto.tesla.xray.acknowledge :as ack]
            [de.otto.tesla.xray.util.utils :as utils]
            [com.stuartsierra.component :as comp]
            [ring.mock.request :as mock]
            [de.otto.tesla.xray.xray-checker :as chkr]
            [de.otto.tesla.stateful.handler :as handler]
            [de.otto.tesla.xray.check :as chk]
            [com.stuartsierra.component :as c]
            [de.otto.tesla.system :as tesla])
  (:import (org.joda.time DateTimeZone DateTime)))

(defrecord ErrorCheck []
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :error "error-message")))

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :xray-checker (c/using (chkr/new-xraychecker "test") [:handler :config :scheduler]))))

(deftest stringify-acknowledged-checks-test
  (testing "should format timestamp properly"
    (with-redefs [ack/as-date-time (fn [millis] (DateTime. millis DateTimeZone/UTC))]
      (is (= "{\"testCheck\":{\"dev\":\"1 November, 08:27\"}}"
             (ack/stringify-acknowledged-checks {:acknowledged-checks (atom {"testCheck" {"dev" 1477988830064}})}))))))

(deftest acknowledge-check-endpoints
         (testing "should put a check object with correct expire time in the acknowledged-checks atom"
                  (with-redefs [utils/current-time (constantly 10)]
                    (let [acknowledged-checks (atom {})]
                      (ack/acknowledge-check! {:check-results        (atom {})
                                                :acknowledged-checks acknowledged-checks}
                                              "oneHourAcknowledgement" "test-env" "1")
                      (is (= ["oneHourAcknowledgement" {"test-env" (+ 10 (* 60 60 1000))}]
                             (first @acknowledged-checks))))))

         (testing "should keep other environments unchanged when adding ack for same check in different env"
                  (with-redefs [utils/current-time (constantly 10)]
                    (let [acknowledged-checks (atom {"oneHourAcknowledgement" {"otherEnv" 20}})]
                      (ack/acknowledge-check! {:check-results        (atom {})
                                                :acknowledged-checks acknowledged-checks}
                                              "oneHourAcknowledgement" "test-env" "1")
                      (is (= ["oneHourAcknowledgement" {"test-env" (+ 10 (* 60 60 1000))
                                                        "otherEnv" 20}] (first @acknowledged-checks))))))

         (testing "should immediatly change the overall status to acknowledged"
                  (let [acknowledged-checks (atom {})
                        check-results (atom {"testCheck" {"test-env" {:overall-status :error}}})]
                    (ack/acknowledge-check! {:check-results        check-results
                                              :acknowledged-checks acknowledged-checks}
                                            "testCheck" "test-env" "1")
                    (is (= {"testCheck" {"test-env" {:overall-status :acknowledged}}}
                           @check-results))))

         (testing "should remove a check object regardless of it's expire time"
                  (let [acknowledged-checks (atom {"checkToBeRemoved" {"testEnv" 1000}})]
                    (ack/remove-acknowledgement! {:acknowledged-checks acknowledged-checks} "checkToBeRemoved" "testEnv")
                    (is (= {} @acknowledged-checks))))
         (testing "should only remove one env and keep the other one"
                  (let [acknowledged-checks (atom {"checkToBeRemoved" {"dropEnv" 1000
                                                                       "keepEnv" 100}})]
                    (ack/remove-acknowledgement! {:acknowledged-checks acknowledged-checks} "checkToBeRemoved" "dropEnv")
                    (is (= {"checkToBeRemoved" {"keepEnv" 100}} @acknowledged-checks)))))

(def start-the-xraychecks #'chkr/start-checks)
(deftest acknowledging-test
  (testing "should acknowledge single check using POST endpoint"
    (let [started (comp/start (test-system {:test-check-frequency    "200000"
                                            :test-check-environments "dev"
                                            :test-max-check-history  "2"}))
          {:keys [xray-checker handler]} started
          handler-fn (handler/handler handler)
          current-time 10
          acknowledge-hours 15
          acknowledge-hours-in-millis (* 15 60 60 1000)]
      (chkr/register-check xray-checker (->ErrorCheck) "ErrorCheck")
      (try
        (with-redefs [utils/current-time (constantly current-time)]
          ;nothing on startup
          (is (= {} @(:acknowledged-checks xray-checker)))
          (is (= {} @(:check-results xray-checker)))

          ;plain error result after first check
          (start-the-xraychecks xray-checker)
          (is (= {} @(:acknowledged-checks xray-checker)))
          (is (= {"ErrorCheck" {"dev" {:overall-status :error
                                       :results        [(chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                 @(:check-results xray-checker)))

          ;ACKNOWLEDGE!
          (is (= 204 (-> (handler-fn (mock/request :post (str "/xray-checker/acknowledged-checks?check-id=ErrorCheck&environment=dev&hours=" acknowledge-hours)))
                         :status)))
          ;acknowledged results
          (start-the-xraychecks xray-checker)
          (is (= {"ErrorCheck" {"dev" (+ acknowledge-hours-in-millis current-time)}}
                 @(:acknowledged-checks xray-checker)))
          (is (= {"ErrorCheck" {"dev" {:overall-status :acknowledged
                                       :results        [(chk/->XRayCheckResult :acknowledged "error-message; Acknowledged" 0 10)
                                                        (chk/->XRayCheckResult :error "error-message" 0 10)]}}}
                 @(:check-results xray-checker))))
        (finally
          (comp/stop started)))))

  (testing "should cleanup acknowledgement after configured time"
    (let [started (comp/start (test-system {:test-check-frequency    "200000"
                                            :test-check-environments "dev"
                                            :test-max-check-history  "2"}))
          {:keys [xray-checker handler]} started
          handler-fn (handler/handler handler)
          start-time 10
          acknowledge-hours 15
          acknowledge-hours-in-millis (* 15 60 60 1000)
          time-after-acknowledged-check (+ start-time acknowledge-hours-in-millis 1)]
      (chkr/register-check xray-checker (->ErrorCheck) "ErrorCheck")
      (try
        (with-redefs [utils/current-time (constantly start-time)]
          ;ACKNOWLEDGE!
          (is (= 204 (-> (handler-fn (mock/request :post (str "/xray-checker/acknowledged-checks?check-id=ErrorCheck&environment=dev&hours=" acknowledge-hours)))
                         :status)))
          ;acknowledged results
          (start-the-xraychecks xray-checker)
          (is (= {"ErrorCheck" {"dev" (+ acknowledge-hours-in-millis start-time)}}
                 @(:acknowledged-checks xray-checker)))
          (is (= {"ErrorCheck" {"dev" {:overall-status :acknowledged
                                       :results        [(chk/->XRayCheckResult :acknowledged "error-message; Acknowledged" 0 start-time)]}}}
                 @(:check-results xray-checker))))

        (with-redefs [utils/current-time (constantly time-after-acknowledged-check)]
          (start-the-xraychecks xray-checker)
          (is (= {} @(:acknowledged-checks xray-checker)))
          (is (= {"ErrorCheck" {"dev" {:overall-status :error
                                       :results        [(chk/->XRayCheckResult :error "error-message" 0 time-after-acknowledged-check)
                                                        (chk/->XRayCheckResult :acknowledged "error-message; Acknowledged" 0 start-time)]}}}
                 @(:check-results xray-checker))))
        (finally
          (comp/stop started)))))

  (testing "should delete acknowledgement"
    (let [started (comp/start (test-system {:test-check-frequency    "200000"
                                            :test-check-environments "dev"
                                            :test-max-check-history  "2"}))
          {:keys [xray-checker handler]} started
          handler-fn (handler/handler handler)
          start-time 10
          acknowledge-hours 15
          acknowledge-hours-in-millis (* 15 60 60 1000)
          time-after-acknowledged-check (+ start-time acknowledge-hours-in-millis 1)]
      (chkr/register-check xray-checker (->ErrorCheck) "ErrorCheck")
      (try
        (with-redefs [utils/current-time (constantly start-time)]
          ;ACKNOWLEDGE!
          (is (= 204 (-> (handler-fn (mock/request :post (str "/xray-checker/acknowledged-checks?check-id=ErrorCheck&environment=dev&hours=" acknowledge-hours)))
                         :status)))
          (is (= {"ErrorCheck" {"dev" (+ acknowledge-hours-in-millis start-time)}} @(:acknowledged-checks xray-checker)))
          ;DELETE ACKNOWLEDGEMENT!
          (is (= 204 (-> (handler-fn (mock/request :delete "/xray-checker/acknowledged-checks/ErrorCheck/dev"))
                         :status)))
          (is (= {} @(:acknowledged-checks xray-checker))))
        (finally
          (comp/stop started))))))

(deftest clear-outdated-acknowledgements!
  (testing "should clear outdated acknowledgements"
    (with-redefs [utils/current-time (constantly 20)]
      (let [state (atom {"CheckA" {"dev"  20
                                   "prod" 30}
                         "CheckB" {"dev"  30
                                   "prod" 10}
                         "CheckC" {"prod" 10}})]
        (ack/clear-outdated-acknowledgements! {:acknowledged-checks state})
        (is (= {"CheckA" {"prod" 30}
                "CheckB" {"dev" 30}}
               @state)))))

  (testing "should acknowledge check and clear acknowledgements"
    (let [mock-time 20]
      (with-redefs [utils/current-time (constantly mock-time)]
        (let [check-results (atom {})
              hour-as-millis (* 60 60 1000)
              acknowledged-checks (atom {})]
          (ack/acknowledge-check! {:check-results       check-results
                                    :acknowledged-checks acknowledged-checks}
                                   "CheckA" "dev" "1")
          (is (= {"CheckA" {"dev" (+ (* 1 hour-as-millis) mock-time)}} @acknowledged-checks))
          (ack/acknowledge-check! {:check-results       check-results
                                    :acknowledged-checks acknowledged-checks}
                                   "CheckB" "prod" "1")
          (is (= {"CheckA" {"dev" (+ (* 1 hour-as-millis) mock-time)}
                  "CheckB" {"prod" (+ (* 1 hour-as-millis) mock-time)}} @acknowledged-checks))
          (with-redefs [utils/current-time (constantly (+ (* 2 hour-as-millis) mock-time))]
            (ack/clear-outdated-acknowledgements! {:acknowledged-checks acknowledged-checks})
            (is (= {} @acknowledged-checks))
            (ack/clear-outdated-acknowledgements! {:acknowledged-checks acknowledged-checks})
            (is (= {} @acknowledged-checks))))))))