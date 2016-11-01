(ns de.otto.tesla.xray.ui.svg)

(def default-polyline-settings
  {:fill         "#2c3e50"
   :stroke       "white"
   :stroke-width "2"})

(def default-svg {:xmlns       "http://www.w3.org/2000/svg"
                  :xmlns:xlink "http://www.w3.org/1999/xlink"
                  :viewBox     "0 0 100 100"
                  :height      "100%"
                  :width       "100%"
                  :xml:space   "preserve"})

(defn up-icon [& {:as input}]
  (let [{:keys [fill stroke stroke-width]} (merge default-polyline-settings input)]
    [:div {:class "svg-icon up-icon"}
     [:svg default-svg
      [:polyline {:fill            fill
                  :stroke          stroke
                  :stroke-width    stroke-width
                  :stroke-linecap  "round"
                  :stroke-linejoin "round"
                  :points          "10,90 50,10 90,90 10,90"}]]]))

(defn down-icon [& {:as input}]
  (let [{:keys [fill stroke stroke-width]} (merge default-polyline-settings input)]
    [:div {:class "svg-icon down-icon"}
     [:svg default-svg
      [:polyline {:fill            fill
                  :stroke          stroke
                  :stroke-width    stroke-width
                  :stroke-linecap  "round"
                  :stroke-linejoin "round"
                  :points          "10,10 50,90 90,10 10,10"}]]]))

(defn submit-icon [& {:as input}]
  (let [{:keys [fill stroke stroke-width]} (merge default-polyline-settings input)]
    [:div {:class "svg-icon submit-icon"}
     [:svg default-svg
      [:polyline {:fill            fill
                  :stroke          stroke
                  :stroke-width    stroke-width
                  :stroke-linecap  "round"
                  :stroke-linejoin "round"
                  :points          "10,90 90,50 10,10 30,50 10,90"}]]]))

(defn done-icon [& {:as input}]
  (let [{:keys [fill stroke stroke-width]} (merge default-polyline-settings input)]
    [:div {:class "svg-icon done-icon"}
     [:svg default-svg
      [:polyline {:fill            fill
                  :stroke          stroke
                  :stroke-width    stroke-width
                  :stroke-linecap  "round"
                  :stroke-linejoin "round"
                  :points          "10,60 30,90 80,15 70,6 30,70 20,45 10,60"}]]]))
