(ns de.otto.tesla.xray.ui.env-overview
  (:require [hiccup.page :as hc]
            [de.otto.tesla.xray.check :as chk]
            [clj-time.coerce :as time]))

(defn- single-check-result-as-html [{:keys [status message time-taken stop-time]}]
  (let [stop-time-str (if stop-time (time/from-long stop-time))
        text (str stop-time-str " tt:" time-taken " " message)]
    [:div {:class (str "env-single-results " (name status))} text]))

(defn- render-results-for-env [strategy total-cols nr-checks-displayed [env {:keys [results]}]]
  (let [overall-status (strategy results)
        width (int (/ 97 total-cols))
        padding (int (/ 3 total-cols))]
    [:div {:class "env-results-container" :style (str "width: " width "%; padding-left: " padding "%;")}
     [:div {:class (str "overall-" (name overall-status))}
      [:div {:class (str "env-header " (name overall-status))} env]
      (map single-check-result-as-html (take nr-checks-displayed results))]]))

(defn- sort-results-by-env [results-for-env environments]
  (sort-by (fn [[env _]] (.indexOf environments env)) results-for-env))

(defn- check-results-as-html [environments checks nr-checks-displayed [checkname results-for-env]]
  (let [strategy (get-in @checks [checkname :strategy] chk/default-strategy)]
    [:div {:class "check-results"}
     [:div {:class "check-header"} checkname]
     (map (partial render-results-for-env strategy (count results-for-env) nr-checks-displayed) (sort-results-by-env results-for-env environments))]))

(defn render-env-overview [{:keys [check-results checks environments nr-checks-displayed]}]
  (hc/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:title "XRayCheck Results"]
     (hc/include-css "/stylesheets/base.css")]
    [:body
     [:header
      [:h1 "XRayCheck Results"]]
     [:div {:class "check-result-container"}
      (map (partial check-results-as-html environments checks nr-checks-displayed) @check-results)]]))