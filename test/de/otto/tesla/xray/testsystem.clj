(ns de.otto.tesla.xray.testsystem
  (:require [de.otto.tesla.xray.xray-checker :as chkr]
            [com.stuartsierra.component :as c]
            [me.lomin.component-restart :as restart]
            [de.otto.tesla.system :as tesla]
            [de.otto.tesla.serving-with-jetty :as serving-with-jetty]
            [de.otto.tesla.xray.check :as chk]
            [clojure.tools.logging :as log])
  (:gen-class))

(defn random-result []
  (case (rand-int 10)
    1 (chk/->XRayCheckResult :error "error")
    2 (chk/->XRayCheckResult :warning "warning")
    3 (chk/->XRayCheckResult :acknowledged "acknowledged")
    (chk/->XRayCheckResult :ok "ok")))

(defrecord Check [check-id title]
  c/Lifecycle
  (start [self]
    (chkr/register-check (:rt-checker self) self check-id title)
    self)
  (stop [self] self)

  chk/XRayCheck
  (start-check [_ _]
    (random-result)))

(defn test-system [runtime-config]
  (-> (tesla/base-system (assoc runtime-config :name "test-system"))
      (assoc
        :rt-checker (c/using (chkr/new-xraychecker "test") [:handler :config :scheduler])
        :check0 (c/using (->Check "check0" "Checking system 0") [:rt-checker])
        :check1 (c/using (->Check "check1" "Checking system 1 & No encoding problem") [:rt-checker])
        :check2 (c/using (->Check "check2" "Checking system 2") [:rt-checker])
        :check3 (c/using (->Check "check3" "Checking system 3 + Something more") [:rt-checker])
        :check4 (c/using (->Check "check4" "Checking system 4") [:rt-checker])
        :check5 (c/using (->Check "check5" "Checking system 5") [:rt-checker])
        :check6 (c/using (->Check "check6" "Checking system 6") [:rt-checker]))
      (serving-with-jetty/add-server :rt-checker)))

(defn -main [& args]
  (let [started (tesla/start (test-system {:test-check-frequency     "30000"
                                           :test-nr-checks-displayed "0"
                                           :test-check-environments  "dev;test;prod"}))]
    (log/info "test-system started. Goto http://localhost:8080/xray-checker")
    (restart/watch (var -main) started)))
