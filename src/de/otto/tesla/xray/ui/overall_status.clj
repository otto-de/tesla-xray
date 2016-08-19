(ns de.otto.tesla.xray.ui.overall-status
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.util.utils :as utils]))

(defn- flat-results [check-results]
  (mapcat vals (vals @check-results)))

(defn no-check-started-for-too-long-time [last-check refresh-frequency]
  (or
    (nil? @last-check)
    (>= (- (utils/current-time) @last-check) (* 2 refresh-frequency))))

(defn calc-overall-status [check-results last-check refresh-frequency]
  (let [all-status (map :overall-status (flat-results check-results))]
    (if (no-check-started-for-too-long-time last-check refresh-frequency)
      :defunct
      (if (some #(= :error %) all-status)
        :error
        (if (some #(= :warning %) all-status)
          :warning
          :ok)))))

(defn render-overall-status-container [check-results last-check {:keys [refresh-frequency endpoint]}]
  (let [the-overall-status (name (calc-overall-status check-results last-check refresh-frequency))]
    [:a {:href (str endpoint "/overview")}
     [:div {:class (str "overall-status-page " the-overall-status)}
      the-overall-status]]))

(defn render-overall-status [check-results last-check xray-config]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "refresh" :content (/ (:refresh-frequency xray-config) 1000)}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css" "/stylesheets/overall-status.css")]
    [:body
     [:header
      [:h1 "XRayCheck Overall-Status"]
      [:span {:class "last-check"}
       [:h2 "Last check: " (utils/readable-timestamp @last-check)]]]
     (render-overall-status-container check-results last-check xray-config)]))
