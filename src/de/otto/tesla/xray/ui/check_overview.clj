(ns de.otto.tesla.xray.ui.check-overview
  (:require [ring.util.codec :as co]
            [de.otto.tesla.xray.ui.overall-status :as os]
            [de.otto.tesla.xray.ui.utils :as uu]
            [de.otto.tesla.xray.ui.layout :as layout]))

(defn- single-result [{:keys [status message time-taken stop-time]}]
  [:div.result.status {:class (name status)}
   (or (uu/readable-timestamp stop-time) "")
   "tt:"
   time-taken
   message])

(defn- results-html [env nr-checks-displayed results]
  [:div
   [:header env]
   (for [r (take nr-checks-displayed results)]
     (single-result r))])

(defn results-for-env
  ([nr-checks-displayed [env {:keys [results overall-status]}]]
   (let [url-encoded-env (co/url-encode env)]
     [:div.env-result.status {:class (name overall-status)}
      (results-html env nr-checks-displayed results)]))
  ([nr-checks-displayed [env {:keys [results overall-status]}] check-id endpoint]
   (let [url-encoded-env (co/url-encode env)]
     [:div.env-result.status {:class (name overall-status)}
      [:a {:href (str endpoint "/checks/" check-id "/" url-encoded-env)}
       (results-html env nr-checks-displayed results)]])))

(defn- envs-for-check [registered-checks {:keys [environments nr-checks-displayed endpoint]} [check-id results-for-check]]
  (let [sorted-results (uu/sort-results-by-env results-for-check environments)]
    [:article.check
     [:header (get-in registered-checks [check-id :title])]
     (into [:div.results]
           (for [result sorted-results]
             (results-for-env nr-checks-displayed result check-id endpoint)))]))

(defn summarize-ok-checks [registered-checks xray-config ok-checks]
  [:article.check
   [:header "All OK checks"]
   [:div.results
    [:div.env-result.status.ok
     [:header (str (count ok-checks) " checks are completely OK!")]
     (into [:section.titles]
           (for [[id check] ok-checks]
             [:a {:href (str (:endpoint xray-config) "/checks/" id)}
              (get-in registered-checks [id :title])]))]]])

(defn overview-page [{:keys [check-results last-check xray-config]} back-link checks-section]
  (let [{:keys [refresh-frequency]} xray-config
        overall-status (name (os/calc-overall-status check-results last-check refresh-frequency))]
    (layout/page refresh-frequency
                 [:body.overview
                  [:header
                   back-link
                   "Last check: " (uu/readable-timestamp @last-check)]

                  [:section {:class (str "status " overall-status)}
                   overall-status]

                  checks-section])))

(defn check-overview [{:keys [registered-checks check-results xray-config] :as xray-checker}]
  (overview-page
    xray-checker
    [:a.back {:href (:endpoint xray-config)} "< back"]
    (let [{:keys [all-ok some-not-ok]} (uu/separate-completely-ok-checks @check-results)]
      [:section.checks
       (map (partial envs-for-check @registered-checks xray-config) some-not-ok)
       (summarize-ok-checks @registered-checks xray-config all-ok)])))

(defn single-check [{:keys [registered-checks check-results xray-config] :as xray-checker} check-id]
  (overview-page
    xray-checker
    [:a.back {:href (str (:endpoint xray-config) "/checks")} "< back"]
    [:section.checks
     (envs-for-check @registered-checks xray-config [check-id (get @check-results check-id)])]))
