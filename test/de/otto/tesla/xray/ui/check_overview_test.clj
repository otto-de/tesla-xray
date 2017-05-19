(ns de.otto.tesla.xray.ui.check-overview-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.ui.check-overview :as ov]))

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
                                   {:endpoint "http://example.com"}
                                   {"check1" {"dev"  {:overall-status :ok}
                                              "qa"   {:overall-status :ok}
                                              "prod" {:overall-status :ok}}
                                    "check2" {"dev"  {:overall-status :ok}
                                              "qa"   {:overall-status :ok}
                                              "prod" {:overall-status :ok}}})))))
