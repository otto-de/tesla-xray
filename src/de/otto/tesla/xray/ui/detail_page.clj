(ns de.otto.tesla.xray.ui.detail-page
  (:require [de.otto.tesla.xray.ui.check-overview :as eo]
            [de.otto.tesla.xray.ui.layout :as layout]
            [de.otto.tesla.xray.ui.utils :as uu]))

(defn ack-form [endpoint check-id current-env acknowledge-hours-to-expire]
  [:form {:method "POST" :data-method "POST" :action (str endpoint "/acknowledged-checks") :id "set-ack"}
   [:div [:span.label "Set acknowlegment for some hours:"]]
   [:input {:type "number" :name "hours" :value acknowledge-hours-to-expire}]
   [:input {:type "hidden" :name "check-id" :value check-id}]
   [:input {:type "hidden" :name "environment" :value current-env}]
   [:input {:type "submit" :value "ack"}]])

(defn del-form [endpoint check-id current-env]
  [:form {:data-method "DELETE" :action (str endpoint "/acknowledged-checks/" check-id "/" current-env) :id "del-ack"}
   [:div [:span.label "Reset acknowlegment:"]]
   [:input {:type "submit" :value "reset"}]])

(defn acknowledge-section [acknowledged-checks acknowledge-hours-to-expire endpoint check-id current-env]
  (let [end-time (get-in @acknowledged-checks [check-id current-env])]
    [:section.acknowledge
     [:header "Acknowledgement"]

     (if end-time
       [:div
        [:div
         [:span.label "active since:"]
         [:span.value (uu/readable-timestamp end-time)]]
        [:div
         [:span.label "time left:"]
         [:span.value (uu/time-left end-time)]]]
       [:div
        [:span.value "Not acknowlegded"]])

     (if end-time
       (del-form endpoint check-id current-env)
       (ack-form endpoint check-id current-env acknowledge-hours-to-expire))]))

(defn results [check-results acknowledged-checks {:keys [max-check-history endpoint acknowledge-hours-to-expire]} check-id current-env]
  (if-let [check-results (get-in @check-results [check-id current-env])]
    [:section.checks
     [:article.check
      (acknowledge-section acknowledged-checks acknowledge-hours-to-expire endpoint check-id current-env)
      [:div.results
       (eo/results-for-env max-check-history [current-env check-results])]]]
    [:div "NO DATA FOUND"]))

(defn detail-page [{:keys [registered-checks check-results acknowledged-checks xray-config]} check-id current-env]
  (let [{:keys [refresh-frequency endpoint]} xray-config]
    (layout/page refresh-frequency
                 [:body.detail
                  [:header
                   [:a.back {:href (str endpoint "/checks")} "< back"]
                   [:h1 (get-in @registered-checks [check-id :title])]]
                  (results check-results acknowledged-checks xray-config check-id current-env)])))
