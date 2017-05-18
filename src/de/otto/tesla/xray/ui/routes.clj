(ns de.otto.tesla.xray.ui.routes
  (:require [compojure.core :as comp]
            [de.otto.tesla.xray.ui.overall-status :as oas]
            [de.otto.tesla.xray.ui.env-overview :as eo]
            [de.otto.tesla.xray.ui.detail-page :as dp]))

(defn routes [xray-checker]
  (let [endpoint (get-in xray-checker [:xray-config :endpoint])]
    (comp/routes
      (comp/GET endpoint []
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (oas/render-overall-status xray-checker)})

      (comp/GET (str endpoint "/checks") []
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (eo/render-env-overview xray-checker)})

      (comp/GET (str endpoint "/checks/:check-id") [check-id]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (eo/render-single-check xray-checker check-id)})
      
      (comp/GET (str endpoint "/checks/:check-id/:environment") [check-id environment]
        {:status  200
         :headers {"Content-Type" "text/html"}
         :body    (dp/render-detail-page xray-checker check-id environment)}))))
