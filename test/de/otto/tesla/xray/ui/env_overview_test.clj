(ns de.otto.tesla.xray.ui.env-overview-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.ui.env-overview :as ov]))

(def sort-results-by-env #'ov/sort-results-by-env)
(deftest sorting-results
  (testing "should sort results by env"
    (is (= `("D" "A" "C" "B")
           (keys
             (sort-results-by-env {"B" ["B"]
                                   "C" ["C"]
                                   "D" ["D"]
                                   "A" ["A"]} ["D" "A" "C" "B"]))))))
