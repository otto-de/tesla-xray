(ns de.otto.tesla.xray.ui.overall-status
  (:require [de.otto.tesla.xray.ui.layout :as layout]
            [de.otto.tesla.xray.util.utils :as utils]))

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
    (layout/page refresh-frequency
      [:body.overall
       [:header
        "Last check: " (utils/readable-timestamp @last-check)]

       [:a {:href (str endpoint "/overview")}
        [:section {:class (str "status " overall-status)}
         overall-status]]])))
