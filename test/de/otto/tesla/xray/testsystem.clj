(ns de.otto.tesla.xray.testsystem
  (:require [de.otto.tesla.xray.xray-checker :as chkr]
            [com.stuartsierra.component :as c]
            [me.lomin.component-restart :as restart]
            [de.otto.tesla.system :as tesla]
            [de.otto.tesla.serving-with-jetty :as serving-with-jetty]
            [de.otto.tesla.xray.check :as chk]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn random-result [_]
  (case (rand-int 3)
    0 (chk/->XRayCheckResult :ok "")
    1 (chk/->XRayCheckResult :error "")
    2 (chk/->XRayCheckResult :warning "")))

(defn n-random-results [n]
  (into [] (map random-result (range n))))

(def some-data
  {"CheckA" {"dev"  (n-random-results 50)
             "test" (n-random-results 100)
             "prod" (n-random-results 10)}
   "CheckB" {"dev"  (n-random-results 20)
             "test" (n-random-results 60)
             "prod" (n-random-results 20)}
   "CheckC" {"dev"  (n-random-results 70)
             "test" (n-random-results 40)
             "prod" (n-random-results 30)}
   "CheckD" {"dev"  (n-random-results 50)
             "test" (n-random-results 24)
             "prod" (n-random-results 5)}})

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))
      (serving-with-jetty/add-server :rt-checker)))

(defn -main [& args]
  (let [{:keys [rt-checker] :as started} (tesla/start (test-system {}))]
    (reset! (:check-results rt-checker) some-data)
    (log/info "test-system started")
    (restart/watch (var -main) started)))
