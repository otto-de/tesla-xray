(ns de.otto.tesla.xray.ui.env-overview
  (:require [ring.util.codec :as co]
            [de.otto.tesla.xray.ui.overall-status :as os]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.xray.ui.layout :as layout]))

(defn- single-check-result-as-html [{:keys [status message time-taken stop-time]}]
  (let [stop-time-str (or (utils/readable-timestamp stop-time) "")
        text (str stop-time-str " tt:" time-taken " " message)]
    [:div.result.status {:class (name status)} text]))

(defn link-to-detail-page [show-links? endpoint check-name current-env the-html]
  (let [url-ecoded-check-name (co/url-encode check-name)
        url-encoded-env (co/url-encode current-env)]
    (if show-links?
      [:a {:href (str endpoint "/detail/" url-ecoded-check-name "/" url-encoded-env)} the-html]
      the-html)))

(defn render-results-for-env [nr-checks-displayed checkname endpoint show-links? [env {:keys [results overall-status]}]]
  (let [should-show-links? (and (not (= overall-status :none)) show-links?)]
    [:div.env-result.status {:class (name overall-status)}
     (link-to-detail-page should-show-links? endpoint checkname env
                          [:div
                           [:header env]
                           (map single-check-result-as-html (take nr-checks-displayed results))])]))

(defn- sort-results-by-env [results-for-env environments]
  (sort-by (fn [[env _]] (.indexOf environments env)) results-for-env))

(defn- check-results-as-html [{:keys [environments nr-checks-displayed endpoint]} [checkname results-for-env]]
  (let [show-links true
        sorted-results (sort-results-by-env results-for-env environments)]
    [:article.check
     [:header checkname]
     [:div.results
      (map (partial render-results-for-env nr-checks-displayed checkname endpoint show-links) sorted-results)]]))

(defn render-env-overview [check-results last-check {:keys [endpoint refresh-frequency] :as xray-config}]
  (let [overall-status (name (os/calc-overall-status check-results last-check refresh-frequency))]
    (layout/page refresh-frequency
                 [:body.overview
                  [:header
                   [:a.back {:href endpoint} "< back"]
                   "Last check: " (utils/readable-timestamp @last-check)]

                  [:section {:class (str "status " overall-status)}
                   overall-status]

                  [:section.checks
                   (map (partial check-results-as-html xray-config) @check-results)]])))
