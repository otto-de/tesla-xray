(ns de.otto.tesla.xray.ui.layout
  (:require [hiccup.page :as hiccup]
            [clojure.java.io :as io]))

(defn page [refresh-frequency content]
  (hiccup/html5
    [:head
     [:meta {:charset "utf-8"}]
     [:meta {:http-equiv "refresh" :content (/ refresh-frequency 1000)}]
     [:title "XRayCheck Results"]
     (hiccup/include-css "/stylesheets/application.css")
     (when (io/resource "public/stylesheets/custom.css")
       (hiccup/include-css "/stylesheets/custom.css"))]

    content
    
    (hiccup/include-js "/js/application.js")))
