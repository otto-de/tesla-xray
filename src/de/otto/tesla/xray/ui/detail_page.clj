(ns de.otto.tesla.xray.ui.detail-page
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.xray.ui.svg :as svg]
            [clojure.java.io :as io]))

(defn rendered-check-results [check-results acknowledged-checks {:keys [max-check-history endpoint acknowledge-hours-to-expire]} check-name current-env]
  (let [show-links false]
    [:div {:class "detail-page-container"}
     [:div {:class "chkmenu"}

      [:div {:class "chkmenu-header"}
       "Acknowledgement"]

      [:div {:class (str "chkmenuitem-row " (if (get-in @acknowledged-checks [check-name current-env]) "ackstatus-active" "ackstatus-inactive"))
             :style "text-align: center;"}
       (svg/done-icon)]

      [:div {:class "chkmenuitem-row"}
       [:div {:class "chkmenuitem-centered-text"}
        [:p
         (if-let [end-time (get-in @acknowledged-checks [check-name current-env])]
           (utils/readable-timestamp end-time)
           "not set")]]]

      [:div {:class "chkmenuitem-row"}
       [:div {:class "chkmenuitem-centered-text"}
        [:p
         (if-let [end-time (get-in @acknowledged-checks [check-name current-env])]
           (let [millis (max (- end-time (System/currentTimeMillis)) 0)
                 seconds (int (mod (/ millis 1000) 60))
                 minutes (int (mod (/ millis (* 1000 60)) 60))
                 hours (int (mod (/ millis (* 1000 60 60)) 24))]
             (format "%sh %smin %ssec left" hours minutes seconds))
           "not set")]]]

      [:div {:class "chkmenuitem-row"}
       [:div {:class "acknowledgement-input"
              :id    "acknowledgement-input-value"
              :style "width: 40%"}
        acknowledge-hours-to-expire]

       [:div {:class   "acknowledgement-button"
              :style   "width: 20%;"
              :onClick "onAcknowledgementDecrease()"}
        (svg/down-icon)]

       [:div {:class   "acknowledgement-button"
              :style   "width: 20%;"
              :onClick "onAcknowledgementIncrease()"}
        (svg/up-icon)]

       [:div {:class   "acknowledgement-button"
              :style   "width: 20%;"
              :onClick (str "onAcknowledgementClick(\"" endpoint "\", \"" check-name "\", \"" current-env "\")")}
        (svg/submit-icon)]]]
     [:div {:class "single-check-results"}
      (eo/render-results-for-env 1 max-check-history check-name endpoint show-links [current-env check-results])]]))

(defn detail-page-content [check-results acknowledged-checks xray-conf check-name current-env]
  (if-let [check-results (get-in @check-results [check-name current-env])]
    (rendered-check-results check-results acknowledged-checks xray-conf check-name current-env)
    [:div {:class "detail-page-container"} "NO DATA FOUND"]))

(defn render-detail-page [check-results acknowledged-checks {:keys [endpoint refresh-frequency] :as xray-config} check-name current-env]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "refresh" :content (/ refresh-frequency 1000)}]
     [:title "XRayCheck Results"]
     (hc/include-js "/js/main.js")
     (hc/include-css "/stylesheets/base.css" "/stylesheets/detail-page.css")
     (when (io/resource "public/stylesheets/custom.css")
       (hc/include-css "/stylesheets/custom.css"))]
    [:body
     [:header
      [:h1 [:a {:class "index-link" :href (str endpoint "/overview")} "<-"] check-name]]
     (detail-page-content check-results acknowledged-checks xray-config check-name current-env)]))
