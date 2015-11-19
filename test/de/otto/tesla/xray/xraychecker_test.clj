(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]))

(def build-check-name-env-vecs #'chkr/build-check-name-env-vecs)
(deftest building-parameters-for-futures
  (testing "should build a propper parameter vector for all checks"
    (is (= [["foo" "CheckA" "dev"] ["foo" "CheckA" "test"]
            ["bar" "CheckB" "dev"] ["bar" "CheckB" "test"]]
           (build-check-name-env-vecs ["dev" "test"] (atom {"CheckA" {:check "foo"}
                                                            "CheckB" {:check "bar"}}))))))

