(ns de.otto.tesla.xray.ui.env-overview
  (:require [ring.util.codec :as co]
            [de.otto.tesla.xray.ui.overall-status :as os]
            [de.otto.tesla.xray.util.utils :as utils]
            [de.otto.tesla.xray.ui.layout :as layout]))

(defn- single-check-result-as-html [{:keys [status message time-taken stop-time]}]
  (let [stop-time-str (or (utils/readable-timestamp stop-time) "")
        text          (str stop-time-str " tt:" time-taken " " message)]
    [:div.result.status {:class (name status)} text]))

(defn link-to-detail-page [show-links? endpoint check-id current-env the-html]
  (let [url-ecoded-check-id (co/url-encode check-id)
        url-encoded-env     (co/url-encode current-env)]
    (if show-links?
      [:a {:href (str endpoint "/checks/" url-ecoded-check-id "/" url-encoded-env)} the-html]
      the-html)))

(defn render-results-for-env [nr-checks-displayed check-id endpoint show-links? [env {:keys [results overall-status]}]]
  (let [should-show-links? (and (not (= overall-status :none)) show-links?)]
    [:div.env-result.status {:class (name overall-status)}
     (link-to-detail-page should-show-links? endpoint check-id env
                          [:div
                           [:header env]
                           (map single-check-result-as-html (take nr-checks-displayed results))])]))

(defn- sort-results-by-env [results-for-env environments]
  (sort-by (fn [[env _]] (.indexOf environments env)) results-for-env))

(defn- check-results-as-html [registered-checks {:keys [environments nr-checks-displayed endpoint]} [check-id results-for-env]]
  (let [show-links     true
        sorted-results (sort-results-by-env results-for-env environments)]
    [:article.check
     [:header (get-in registered-checks [check-id :title])]
     [:div.results
      (map (partial render-results-for-env nr-checks-displayed check-id endpoint show-links) sorted-results)]]))

(defn overall-status-ok? [[_env {:keys [overall-status]}]]
  (contains? #{:ok :none} overall-status))

(defn all-ok? [[_check-id all-env-result]]
  (every? overall-status-ok? all-env-result))

(defn separate-completely-ok-checks [check-results]
  (->> check-results
       (group-by all-ok?)
       (map (fn [[k v]] [(if k :all-ok :some-not-ok) (into {} v)]))
       (into {})))

(defn summarize-ok-checks [registered-checks endpoint ok-checks]
  [:article.check
   [:header "All OK checks"]
   [:div.results
    [:div.env-result.status.ok
     [:header (str (count ok-checks) " checks are completely OK!")]
     (into [:section.titles]
           (for [[id check] ok-checks]
             [:a {:href (str endpoint "/checks/" id)}
              (get-in registered-checks [id :title])]))]]])

(defn render-env-overview [{:keys [registered-checks check-results last-check xray-config]}]
  (let [{:keys [refresh-frequency endpoint]} xray-config
        overall-status (name (os/calc-overall-status check-results last-check refresh-frequency))]
    (layout/page refresh-frequency
                 [:body.overview
                  [:header
                   [:a.back {:href endpoint} "< back"]
                   "Last check: " (utils/readable-timestamp @last-check)]

                  [:section {:class (str "status " overall-status)}
                   overall-status]

                  (let [{:keys [all-ok some-not-ok]} (separate-completely-ok-checks @check-results)]
                    [:section.checks
                     (map (partial check-results-as-html @registered-checks xray-config) some-not-ok)
                     (summarize-ok-checks @registered-checks endpoint all-ok)])])))

(defn render-single-check [{:keys [registered-checks check-results last-check xray-config]} check-id]
  (let [{:keys [refresh-frequency endpoint]} xray-config
        overall-status (name (os/calc-overall-status check-results last-check refresh-frequency))]
    (layout/page refresh-frequency
                 [:body.overview
                  [:header
                   [:a.back {:href (str endpoint "/checks")} "< back"]
                   "Last check: " (utils/readable-timestamp @last-check)]

                  [:section {:class (str "status " overall-status)}
                   overall-status]

                  [:section.checks
                   (check-results-as-html @registered-checks xray-config [check-id (get @check-results check-id)])]])))