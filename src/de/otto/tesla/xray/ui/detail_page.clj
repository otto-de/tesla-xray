(ns de.otto.tesla.xray.ui.detail-page
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.ui.env-overview :as eo]))

(defn render-check-results [check-results {:keys [max-check-history endpoint]} check-name current-env]
  (let [show-links false]
    (if-let [result-map (get-in @check-results [check-name current-env])]
      [:div {:class "single-check-results"}
       (eo/render-results-for-env 1 max-check-history check-name endpoint show-links [current-env result-map])]
      [:div "NO DATA FOUND"])))

(defn render-detail-page [check-results {:keys [endpoint] :as xray-config} check-name current-env]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css")]
    [:body
     [:header
       [:h1 [:a {:class "index-link" :href endpoint}"<-"] check-name]]
     [:div {:class ""}
      (render-check-results check-results xray-config check-name current-env)]]))
