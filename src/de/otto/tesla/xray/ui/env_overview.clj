(ns de.otto.tesla.xray.ui.env-overview
  (:require [hiccup.page :as hc]
            [ring.util.codec :as co]
            [clj-time.coerce :as time]))

(defn- single-check-result-as-html [{:keys [status message time-taken stop-time]}]
  (let [stop-time-str (if stop-time (time/from-long stop-time))
        text (str stop-time-str " tt:" time-taken " " message)]
    [:div {:class (name status)} text]))

(defn wrapped-with-links-to-detail-page [the-html show-links? endpoint check-name current-env]
  (let [url-ecoded-check-name (co/url-encode check-name)
        url-encoded-env (co/url-encode current-env)]
    (if show-links?
      [:a {:href (str endpoint "/detail/" url-ecoded-check-name "/" url-encoded-env)} the-html]
      the-html)))

(defn render-results-for-env [total-cols nr-checks-displayed checkname endpoint show-links? [env {:keys [results overall-status]}]]
  (let [width (int (/ 97 total-cols))
        padding (int (/ 3 total-cols))
        should-show-links? (and
                             (not (= overall-status :none))
                             show-links?)]
    (-> [:div {:class "env-results-container" :style (str "width: " width "%; padding-left: " padding "%;")}
         [:div {:class (str "overall-" (name overall-status))}
          [:div {:class (str "env-header " (name overall-status))} env]
          (map single-check-result-as-html (take nr-checks-displayed results))]]
        (wrapped-with-links-to-detail-page should-show-links? endpoint checkname env))))

(defn- sort-results-by-env [results-for-env environments]
  (sort-by (fn [[env _]] (.indexOf environments env)) results-for-env))

(defn- check-results-as-html [{:keys [environments nr-checks-displayed endpoint]} [checkname results-for-env]]
  (let [show-links true
        sorted-results (sort-results-by-env results-for-env environments)]
    [:div {:class "check-results"}
     [:div {:class "check-header"} checkname]
     (map (partial render-results-for-env (count results-for-env) nr-checks-displayed checkname endpoint show-links) sorted-results)]))

(defn render-env-overview [check-results {:keys [endpoint] :as xray-config}]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css")]
    [:body
     [:header
      [:h1 [:a {:class "index-link" :href endpoint}"<-"] "XRayCheck Results"]]
     [:div {:class "check-result-container"}
      (map (partial check-results-as-html xray-config) @check-results)]]))
