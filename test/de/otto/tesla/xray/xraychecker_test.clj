(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]))

(defrecord DummyCheck [response]
  chk/XRayCheck
  (start-check [_ _]
    (chk/->XRayCheckResult :ok @response)))

(def parse-check-environments #'chkr/parse-check-environments)
(def parse-refresh-frequency #'chkr/parse-refresh-frequency)
(def parse-max-check-history #'chkr/parse-max-check-history)
(def parse-nr-checks-displayed #'chkr/parse-nr-checks-displayed)

(deftest parsing-properties
  (testing "should parse the environment string from config"
    (is (= ["dev" "test"] (parse-check-environments {:config {:foo-check-environments "dev;test"}} "foo"))))
  (testing "should parse empty environment string as nil"
    (is (= [] (parse-check-environments {:config {:foo-check-environments ""}} "foo"))))
  (testing "should parse the frequency from config"
    (is (= 100 (parse-refresh-frequency {:config {:foo-check-frequency "100"}} "foo"))))
  (testing "should parse the max check history"
    (is (= 99 (parse-max-check-history {:config {:foo-max-check-history "99"}} "foo"))))
  (testing "should parse the nr of checks to be displayed"
    (is (= 9 (parse-nr-checks-displayed {:config {:foo-nr-checks-displayed "9"}} "foo")))))


(def build-check-name-env-vecs #'chkr/build-check-name-env-vecs)
(deftest building-parameters-for-futures
  (testing "should build a propper parameter vector for all checks"
    (is (= [["foo" "CheckA" "dev"] ["foo" "CheckA" "test"]
            ["bar" "CheckB" "dev"] ["bar" "CheckB" "test"]]
           (build-check-name-env-vecs ["dev" "test"] (atom {"CheckA" {:check "foo"}
                                                            "CheckB" {:check "bar"}}))))))
