(ns de.otto.tesla.xray.xraychecker-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.xray-checker :as chkr]
    [de.otto.tesla.xray.check :as chk]))

(defrecord DummyCheck [response]
  chk/XRayCheck
  (start-realtime-check [_ _]
    (chk/->XRayCheckResult :ok "dummymsg" @response)))

(deftest test-store-results
  (testing "should store limited results"
    (let [response (atom nil)
          self {:check-results (atom {})
                :checks        (atom {"dummy" (->DummyCheck response)})
                :environments  ["dev"]}]
      (reset! response 1)
      (chkr/start-the-realtimechecks self)
      (reset! response 2)
      (chkr/start-the-realtimechecks self)
      (reset! response 3)
      (chkr/start-the-realtimechecks self)
      (reset! response 4)
      (chkr/start-the-realtimechecks self)
      (reset! response 5)
      (chkr/start-the-realtimechecks self)
      (reset! response 6)
      (chkr/start-the-realtimechecks self)
      (is (= {"dummy" {"dev" [(chk/->XRayCheckResult :ok "dummymsg" 6)
                              (chk/->XRayCheckResult :ok "dummymsg" 5)
                              (chk/->XRayCheckResult :ok "dummymsg" 4)
                              (chk/->XRayCheckResult :ok "dummymsg" 3)
                              (chk/->XRayCheckResult :ok "dummymsg" 2)]}}
             @(:check-results self))))))


(deftest parsing-properties
  (testing "should parse the environment string from config"
    (is (= ["dev" "test"] (chkr/parse-rt-check-environments {:config {:foo-check-environments "dev;test"}} "foo"))))
  (testing "should parse the frequency from config"
    (is (= 100 (chkr/parse-refresh-frequency {:config {:foo-check-frequency "100"}} "foo")))))