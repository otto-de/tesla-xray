(ns de.otto.tesla.xray.ui.utils-test
  (:require [clojure.test :refer :all]
            [de.otto.tesla.xray.ui.utils :as uu]))


(deftest separate-completely-ok-checks-test
  (testing "it groups tests by their overall status"
    (is (= {:all-ok      {"check1" {"dev"  {:overall-status :ok}
                                    "prod" {:overall-status :ok}
                                    "qa"   {:overall-status :ok}}
                          "check4" {"dev"  {:overall-status :ok}
                                    "prod" {:overall-status :ok}
                                    "qa"   {:overall-status :ok}}}
            :some-not-ok {"check2" {"dev"  {:overall-status :warning}
                                    "prod" {:overall-status :ok}
                                    "qa"   {:overall-status :ok}}
                          "check3" {"dev"  {:overall-status :ok}
                                    "prod" {:overall-status :ok}
                                    "qa"   {:overall-status :error}}}}
           (uu/separate-completely-ok-checks
             {"check1" {"dev"  {:overall-status :ok}
                        "qa"   {:overall-status :ok}
                        "prod" {:overall-status :ok}}
              "check2" {"dev"  {:overall-status :warning}
                        "qa"   {:overall-status :ok}
                        "prod" {:overall-status :ok}}
              "check3" {"dev"  {:overall-status :ok}
                        "qa"   {:overall-status :error}
                        "prod" {:overall-status :ok}}
              "check4" {"dev"  {:overall-status :ok}
                        "qa"   {:overall-status :ok}
                        "prod" {:overall-status :ok}}}))))
  (testing "checks that don't have all envs can be all-ok, too"
    (is (= {:all-ok      {"check1" {"dev"  {:overall-status :none}
                                    "qa"   {:overall-status :ok}
                                    "prod" {:overall-status :ok}}
                          "check4" {"dev"  {:overall-status :ok}
                                    "qa"   {:overall-status :none}
                                    "prod" {:overall-status :none}}}
            :some-not-ok {"check2" {"dev"  {:overall-status :warning}
                                    "qa"   {:overall-status :ok}
                                    "prod" {:overall-status :ok}}
                          "check3" {"dev"  {:overall-status :ok}
                                    "qa"   {:overall-status :error}
                                    "prod" {:overall-status :ok}}}}
           (uu/separate-completely-ok-checks
             {"check1" {"dev"  {:overall-status :none}
                        "qa"   {:overall-status :ok}
                        "prod" {:overall-status :ok}}
              "check2" {"dev"  {:overall-status :warning}
                        "qa"   {:overall-status :ok}
                        "prod" {:overall-status :ok}}
              "check3" {"dev"  {:overall-status :ok}
                        "qa"   {:overall-status :error}
                        "prod" {:overall-status :ok}}
              "check4" {"dev"  {:overall-status :ok}
                        "qa"   {:overall-status :none}
                        "prod" {:overall-status :none}}})))))

(deftest sorting-results
  (testing "should sort results by env"
    (is (= ["D" "A" "C" "B"]
           (keys
             (uu/sort-results-by-env {"B" ["B"]
                                      "C" ["C"]
                                      "D" ["D"]
                                      "A" ["A"]} ["D" "A" "C" "B"]))))))
