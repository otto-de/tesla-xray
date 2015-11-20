(ns de.otto.tesla.xray.ui.overall-status-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.ui.overall-status :as os]))

(def calc-overall-status #'os/calc-overall-status)
(deftest determining-the-overall-status
  (testing "should determine :ok"
    (let [check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckB" {"dev"  {:overall-status :none}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckC" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckD" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :none}
                                         "prod" {:overall-status :ok}}})]
      (is (= :ok (calc-overall-status check-results)))))
  (testing "should determine :warning"
    (let [check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckB" {"dev"  {:overall-status :none}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :warning}}
                               "CheckC" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :warning}
                                         "prod" {:overall-status :ok}}
                               "CheckD" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :none}
                                         "prod" {:overall-status :ok}}})]
      (is (= :warning (calc-overall-status check-results)))))
  (testing "should determine :ok"
    (let [check-results (atom {"CheckA" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :ok}}
                               "CheckB" {"dev"  {:overall-status :none}
                                         "test" {:overall-status :ok}
                                         "prod" {:overall-status :warning}}
                               "CheckC" {"dev"  {:overall-status :ok}
                                         "test" {:overall-status :warning}
                                         "prod" {:overall-status :ok}}
                               "CheckD" {"dev"  {:overall-status :error}
                                         "test" {:overall-status :none}
                                         "prod" {:overall-status :ok}}})]
      (is (= :error (calc-overall-status check-results))))))


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


