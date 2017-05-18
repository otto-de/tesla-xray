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
           (ov/separate-completely-ok-checks
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
           (ov/separate-completely-ok-checks
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

(deftest summarize-test
  (testing "it summarizes all ok checks"
    (is (= [:article.check
            [:header "All OK checks"]
            [:div.results
             [:div.env-result.status.ok
              [:header "2 checks are completely OK!"]
              [:section.titles
               [:a {:href "http://example.com/checks/check1"}
                "Check 1"]
               [:a {:href "http://example.com/checks/check2"}
                "Check 2"]]]]]
           (ov/summarize-ok-checks {"check1" {:title "Check 1"}
                                    "check2" {:title "Check 2"}}
                                   "http://example.com"
                                   {"check1" {"dev"  {:overall-status :ok}
                                              "qa"   {:overall-status :ok}
                                              "prod" {:overall-status :ok}}
                                    "check2" {"dev"  {:overall-status :ok}
                                              "qa"   {:overall-status :ok}
                                              "prod" {:overall-status :ok}}})))))
