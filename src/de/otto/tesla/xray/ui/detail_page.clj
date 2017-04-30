(ns de.otto.tesla.xray.ui.detail-page
  (:require [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.xray.ui.layout :as layout]))

(defn time-left [end-time]
  (let [millis (max (- end-time (System/currentTimeMillis)) 0)
        seconds (int (mod (/ millis 1000) 60))
        minutes (int (mod (/ millis (* 1000 60)) 60))
        hours (int (mod (/ millis (* 1000 60 60)) 24))]
    (format "%sh %smin %ssec" hours minutes seconds)))

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
         [:span.value (utils/readable-timestamp end-time)]]
        [:div 
         [:span.label "time left:"]
         [:span.value (time-left end-time)]]]
       [:div
        [:span.value "Not acknowlegded"]])

     (if end-time 
       (del-form endpoint check-id current-env)
       (ack-form endpoint check-id current-env acknowledge-hours-to-expire))]))

(defn rendered-check-results [check-results acknowledged-checks {:keys [max-check-history endpoint acknowledge-hours-to-expire]} check-id current-env]
  (let [show-links false]
    [:section.checks
     [:article.check
      (acknowledge-section acknowledged-checks acknowledge-hours-to-expire endpoint check-id current-env)
      [:div.results
       (eo/render-results-for-env max-check-history check-id endpoint show-links [current-env check-results])]]]))

(defn detail-page-content [check-results acknowledged-checks xray-conf check-id current-env]
  (if-let [check-results (get-in @check-results [check-id current-env])]
    (rendered-check-results check-results acknowledged-checks xray-conf check-id current-env)
    [:div "NO DATA FOUND"]))

(defn render-detail-page [check-results acknowledged-checks {:keys [endpoint refresh-frequency] :as xray-config} check-id current-env]
  (layout/page refresh-frequency
               [:body.detail
                [:header
                 [:a.back {:href (str endpoint "/overview")} "< back"]
                 [:h1 check-id]]
                (detail-page-content check-results acknowledged-checks xray-config check-id current-env)]))
