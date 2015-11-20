(ns de.otto.tesla.xray.ui.overall-status
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.util.utils :as utils])
  (:import (org.joda.time DateTime DateTimeZone)))

(defn- flat-results [check-results]
  (mapcat vals (vals @check-results)))

(defn no-check-started-for-too-long-time [last-check refresh-frequency]
  (or
    (nil? @last-check)
    (>= (- (utils/current-time) @last-check) (* 2 refresh-frequency))))

(defn- calc-overall-status [check-results last-check refresh-frequency]
  (let [all-status (map :overall-status (flat-results check-results))]
    (if (no-check-started-for-too-long-time last-check refresh-frequency)
      :error
      (if (some #(= :error %) all-status)
        :error
        (if (some #(= :warning %) all-status)
          :warning
          :ok)))))

(defn- render-overall-status-container [check-results last-check {:keys [refresh-frequency endpoint]}]
  (let [the-overall-status (name (calc-overall-status check-results last-check refresh-frequency))]
    [:a {:href (str endpoint"/overview")}
     [:div {:class (str "overall-status-page " the-overall-status)}
      the-overall-status]]))

(defn readable-timestamp [last-check]
  (if-let [millis @last-check]
    (.toString (DateTime. millis (DateTimeZone/forID "Europe/Berlin")))
    "no check started"))

(defn render-overall-status [check-results last-check xray-config]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css" "/stylesheets/overall-status.css")]
    [:body
     [:header
      [:h1 "XRayCheck Overall-Status"]
      [:h2 "Last check: " (readable-timestamp last-check)]]
     (render-overall-status-container check-results last-check xray-config)]))