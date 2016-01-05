(ns de.otto.tesla.xray.conf.reading-properties-test
  (:require
    [clojure.test :refer :all]
    [de.otto.tesla.xray.conf.reading-properties :as props]))

(deftest parsing-properties
  (testing "should parse the environment string from config"
    (is (= ["dev" "test"] (props/parse-check-environments {:config {:foo-check-environments "dev;test"}} "foo"))))
  (testing "should parse empty environment string as nil"
    (is (= [] (props/parse-check-environments {:config {:foo-check-environments ""}} "foo"))))
  (testing "should parse the frequency from config"
    (is (= 100 (props/parse-refresh-frequency {:config {:foo-check-frequency "100"}} "foo"))))
  (testing "should parse the frequency as nil from config if not set"
    (is (= nil (props/parse-refresh-frequency {:config {:foo-check-frequency ""}} "foo"))))
  (testing "should parse the frequency as nil from config if set to nil"
    (is (= nil (props/parse-refresh-frequency {:config {:foo-check-frequency nil}} "foo"))))
  (testing "should fallback to default when parsing frequency"
    (is (= 60000 (props/parse-refresh-frequency {:config {}} "foo"))))
  (testing "should parse the max check history"
    (is (= 99 (props/parse-max-check-history {:config {:foo-max-check-history "99"}} "foo"))))
  (testing "should parse the nr of checks to be displayed"
    (is (= 9 (props/parse-nr-checks-displayed {:config {:foo-nr-checks-displayed "9"}} "foo")))))
