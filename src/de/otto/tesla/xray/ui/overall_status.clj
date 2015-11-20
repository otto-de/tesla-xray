(ns de.otto.tesla.xray.ui.overall-status
  (:require [hiccup.page :as hc]))

(defn- flat-results [check-results]
  (mapcat vals (vals @check-results)))

(defn- calc-overall-status [check-results]
  (let [all-status (map :overall-status (flat-results check-results))]
    (if (some #(= :error %) all-status)
      :error
      (if (some #(= :warning %) all-status)
        :warning
        :ok))))

(defn- render-overall-status-container [check-results {:keys [endpoint]}]
  (let [the-overall-status (name (calc-overall-status check-results))]
    [:a {:href (str endpoint"/overview")}
     [:div {:class (str "overall-status-page " the-overall-status)}
      the-overall-status]]))

(defn render-overall-status [check-results xray-config]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css" "/stylesheets/overall-status.css")]
    [:body
     [:header
      [:h1 "XRayCheck Overall-Status"]]
     (render-overall-status-container check-results xray-config)]))