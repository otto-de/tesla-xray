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
  (testing "should parse the max check history"
    (is (= 99 (props/parse-max-check-history {:config {:foo-max-check-history "99"}} "foo"))))
  (testing "should parse the nr of checks to be displayed"
    (is (= 9 (props/parse-nr-checks-displayed {:config {:foo-nr-checks-displayed "9"}} "foo"))))
  (testing "should parse the incoming-webhook-url"
    (is (= "https://someurl.com" (props/parse-incoming-webhook-url {:config {:foo-incoming-webhook-url "https://someurl.com"}} "foo"))))
  (testing "should parse undefined incoming-webhook-url as nil 1"
    (is (= nil (props/parse-incoming-webhook-url {:config {:foo-incoming-webhook-url ""}} "foo"))))
  (testing "should parse undefined incoming-webhook-url as nil 2"
    (is (= nil (props/parse-incoming-webhook-url {:config {}} "foo")))))
