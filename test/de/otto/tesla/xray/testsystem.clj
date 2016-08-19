(ns de.otto.tesla.xray.testsystem
  (:require [de.otto.tesla.xray.xray-checker :as chkr]
            [com.stuartsierra.component :as c]
            [me.lomin.component-restart :as restart]
            [de.otto.tesla.system :as tesla]
            [de.otto.tesla.serving-with-jetty :as serving-with-jetty]
            [de.otto.tesla.xray.check :as chk]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn random-tt []
  (rand-int 100))

(defn tstmp []
  (let [ten-minutes (* 1000 60 10)]
    (- (System/currentTimeMillis) (rand-int ten-minutes))))

(defn random-result [_]
  (case (rand-int 4)
    0 (chk/->XRayCheckResult :ok "ok" (random-tt) (tstmp))
    1 (chk/->XRayCheckResult :error "error" (random-tt) (tstmp))
    2 (chk/->XRayCheckResult :warning "warning" (random-tt) (tstmp))
    3 (chk/->XRayCheckResult :none "no status" (random-tt) (tstmp))))

(defn n-random-results [n]
  (let [results (into [] (map random-result (range n)))]
    {:overall-status (chkr/default-strategy results)
     :results        results}))

(def some-data
  {"CheckA"                                    {"dev"  (n-random-results 50)
                                                "test" (n-random-results 100)
                                                "prod" (n-random-results 10)}
   "CheckB"                                    {"dev"  (n-random-results 20)
                                                "test" (n-random-results 60)
                                                "prod" (n-random-results 20)}
   "CheckC"                                    {"dev"  (n-random-results 70)
                                                "test" (n-random-results 40)
                                                "prod" (n-random-results 30)}
   "CheckD with special chars / (/)(\\\")%& {" {"dev"  (n-random-results 50)
                                                "test" (n-random-results 24)
                                                "prod" (n-random-results 5)}})

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config]))
      (serving-with-jetty/add-server :rt-checker)))

(defn -main [& args]
  (let [{:keys [rt-checker] :as started} (tesla/start (test-system {:test-nr-checks-displayed "1"
                                                                    :test-check-environments  "dev;test;prod"}))]
    (reset! (:check-results rt-checker) some-data)
    (log/info "test-system started")
    (restart/watch (var -main) started)))
