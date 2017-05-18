(ns de.otto.tesla.xray.ui.overall-status-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.util.utils :as utils]
    [de.otto.tesla.xray.ui.overall-status :as os]))

(deftest determining-the-overall-status
  (testing "should determine :ok"
    (with-redefs [utils/current-time (constantly 200)]
      (let [last-check (atom 0)
            refresh-frequency 500
            check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :ok}}
                                 "CheckB" {"dev"  {:overall-status :none}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :ok}}})]
        (is (= :ok (os/calc-overall-status check-results last-check refresh-frequency))))))
  (testing "should determine :warning"
    (with-redefs [utils/current-time (constantly 200)]
      (let [last-check (atom 0)
            refresh-frequency 500
            check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :acknowledged}}
                                 "CheckB" {"dev"  {:overall-status :none}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :warning}}})]
        (is (= :warning (os/calc-overall-status check-results last-check refresh-frequency))))))
  (testing "should determine :error"
    (with-redefs [utils/current-time (constantly 200)]
      (let [last-check (atom 0)
            refresh-frequency 500
            check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :error}}
                                 "CheckB" {"dev"  {:overall-status :none}
                                           "test" {:overall-status :acknowledged}
                                           "prod" {:overall-status :warning}}})]
        (is (= :error (os/calc-overall-status check-results last-check refresh-frequency))))))

  (testing "should determine :defunct if checks were not executed for a longer time"
    (with-redefs [utils/current-time (constantly 1000)]
      (let [last-check (atom 0)
            refresh-frequency 500
            check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :ok}}
                                 "CheckB" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :ok}}})]
        (is (= :defunct (os/calc-overall-status check-results last-check refresh-frequency))))))
  (testing "should determine :acknowledged if no checks are warning or error"
    (with-redefs [utils/current-time (constantly 200)]
      (let [last-check (atom 0)
            refresh-frequency 500
            check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :acknowledged}}
                                 "CheckB" {"dev"  {:overall-status :ok}
                                           "test" {:overall-status :ok}
                                           "prod" {:overall-status :ok}}})]
        (is (= :acknowledged (os/calc-overall-status check-results last-check refresh-frequency)))))))


(def flat-results #'os/flat-results)
(deftest flatten-the-results
  (testing "should return flat results only"
    (let [check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckB" {"dev"  {:overall-status :none}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :warning}}})]
      (is (= [{:overall-status :ok}
              {:overall-status :ok}
              {:overall-status :ok}
              {:overall-status :none}
              {:overall-status :ok}
              {:overall-status :warning}] (flat-results check-results))))))
