(ns de.otto.tesla.xray.util.utils-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.util.utils :as utils]))

(deftest timeout-and-return-fallback
         (let [fallback "some return value"
               real-return "made it in time"]
           (testing "Should timeout if function takes longer than set timeout and return fallback value"
                    (is (= (utils/execute-with-timeout 1000 fallback
                                               (Thread/sleep 2000)
                                               real-return)
                           fallback)))
           (testing "Should return actual value if body returns in time"
                    (is (= (utils/execute-with-timeout 1000 fallback
                                               (Thread/sleep 500)
                                               real-return)
                           real-return)))))
