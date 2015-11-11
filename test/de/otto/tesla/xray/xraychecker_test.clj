(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]))

(defrecord DummyCheck [response]
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok @response)))

(def start-the-xraychecks #'chkr/start-the-xraychecks)

(deftest test-store-results
  (with-redefs [chkr/current-time (fn [] 10)]
    (testing "should store limited results"
      (let [response (atom nil)
            self {:check-results (atom {})
                  :max-check-history 3
                  :checks        (atom {"dummy" (->DummyCheck response)})
                  :environments  ["dev"]}]
        (reset! response 1)
        (start-the-xraychecks self)
        (reset! response 2)
        (start-the-xraychecks self)
        (reset! response 3)
        (start-the-xraychecks self)
        (reset! response 4)
        (start-the-xraychecks self)
        (reset! response 5)
        (start-the-xraychecks self)
        (reset! response 6)
        (start-the-xraychecks self)
        (is (= {"dummy" {"dev" [(chk/->XRayCheckResult :ok 6 0 10)
                                (chk/->XRayCheckResult :ok 5 0 10)
                                (chk/->XRayCheckResult :ok 4 0 10)]}}
               @(:check-results self)))))))

(def parse-check-environments #'chkr/parse-check-environments)
(def parse-refresh-frequency #'chkr/parse-refresh-frequency)
(def parse-max-check-history #'chkr/parse-max-check-history)

(deftest parsing-properties
  (testing "should parse the environment string from config"
    (is (= ["dev" "test"] (parse-check-environments {:config {:foo-check-environments "dev;test"}} "foo"))))
  (testing "should parse empty environment string as nil"
    (is (= [] (parse-check-environments {:config {:foo-check-environments ""}} "foo"))))
  (testing "should parse the frequency from config"
    (is (= 100 (parse-refresh-frequency {:config {:foo-check-frequency "100"}} "foo"))))
  (testing "should parse the max check history"
    (is (= 99 (parse-max-check-history {:config {:foo-max-check-history "99"}} "foo")))))