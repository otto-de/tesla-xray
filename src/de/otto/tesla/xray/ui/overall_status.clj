(ns de.otto.tesla.xray.ui.overall-status
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.util.utils :as utils]
            [clojure.java.io :as io]))

(defn- flat-results [check-results]
  (mapcat vals (vals @check-results)))

(defn no-check-started-for-too-long-time [last-check refresh-frequency]
  (or
    (nil? @last-check)
    (>= (- (utils/current-time) @last-check) (* 2 refresh-frequency))))

(defn calc-overall-status [check-results last-check refresh-frequency]
  (let [all-status (map :overall-status (flat-results check-results))]
    (cond
      (no-check-started-for-too-long-time last-check refresh-frequency) :defunct
      (some #(= :error %) all-status) :error
      (some #(= :warning %) all-status) :warning
      (some #(= :acknowledged %) all-status) :acknowledged
      :default :ok)))


(defn render-overall-status [check-results last-check {:keys [refresh-frequency endpoint]}]
  (let [overall-status (name (calc-overall-status check-results last-check refresh-frequency))]
    (hc/html5
      [:head
       [:meta {:charset "utf-8"}]
       [:meta {:http-equiv "refresh" :content (/ refresh-frequency 1000)}]
       [:title "XRayCheck Results"]
       (hc/include-css "/stylesheets/base.css" "/stylesheets/overall-status.css")
       (when (io/resource "public/stylesheets/custom.css")
         (hc/include-css "/stylesheets/custom.css"))]
      [:body
       [:div {:class "overall-status-page-headline"}
        [:p "Last check: " (utils/readable-timestamp @last-check)]]

       [:a {:href (str endpoint "/overview")}
        [:div {:class (str "overall-status-page " overall-status)}
         [:div (.toUpperCase overall-status)]]]])))
