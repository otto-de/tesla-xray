# tesla-xray
tesla-xray is a component for executing and visualizing checks.   
It can be used with the tesla-microservice.

		[de.otto/tesla-xray "0.2.13"]
  
Checks return Check-Results which currently look like this:

			{:status <ok/error>
			:message <your message>}
			
			(->XRayCheckResult :error "That is an error")

	* Checks can pretty much check/assert anything you like, implementation is up to you
	* A unlimited number of custom-written-checks can be registered
	* Registered checks are executed in a configurable time-schedule
	* The check-results are stored and are visualized for you (3 abstraction levels)
	* An alerting function can be registered

Ideas for further features:  

	* App-Status which shows the status of registered checks

## Usage
Add a xray-checker to your system:

		(:require [com.stuartsierra.component :as component]
				  [de.otto.tesla.xray.xray-checker :as checker]
				  [de.otto.tesla.system :as tesla])
					
		(-> (tesla/base-system {})
			(assoc :check (component/using (your-check/new-check) [:xraychecker]))
			(assoc :xraychecker (component/using (checker/new-xraychecker "xraychecker") [:handler :config])))

`Your-Check` could look like this:

		(:require [com.stuartsierra.component :as component]
				  [de.otto.tesla.xray.xray-checker :as checker]
				  [de.otto.tesla.xray.check :as check])
				  
		(defn- default-strategy [results]
		  (:status (first results)))
		  
		(defn- alerting-fn [{:keys [result check-name env]}]
		  (let [{:keys [status message time-taken stop-time]} result]
			(log/error "ALERT: " check-name " failed on " env " at " stop-time " after " time-taken "ms with status " status ". message was: " message)))
		
		(defrecord YourCheck [xraychecker]
		  component/Lifecycle
		  (start [self]
		  	(checker/set-alerting-function xraychecker alerting-fn)
			(checker/register-check-with-strategy xraychecker self "YourAwesomeCheck" default-strategy)
			self)
		  (stop [self]
			self)
		  
		  check/XRayCheck
		  (start-check [self environment]
			(if you-dont-care?
			  (check/->XRayCheckResult :none "I do not care!")
			  (if what-ever-you-like-to-check-is-an-error?
				(check/->XRayCheckResult :error "I found an error")
				(check/->XRayCheckResult :ok "Fromage")))))

## Configuration
These are the currently supported properties:

			yourchecker-check-environments=env1;env2;env3 ;the envs where you want to execute your checks
			yourchecker-check-frequency=60000 ;schedule for executing the checks in ms (execution is done in parallel)
			yourchecker-check-endpoint=/xray-checker ;where the ui shows up
			yourchecker-max-check-history=100 ;nr of checks to keep (in memory)
			yourchecker-nr-checks-displayed=5 ;nr checks to be diplayed for a check/env on /xray-checker/overview
			yourchecker-alerting-schedule-time=300000 ;if a check stays red, how often do you want to send alerts
			
## UI
### Endpoint: /
![Example view of tesla-xray](doc/overall-status.png)
### Endpoint: /overview
![Example view of tesla-xray](doc/overview.png)
### Endpoint: /detail/CheckC/test
![Example view of tesla-xray](doc/detailview.png)


## Initial Contributors

Kai Brandes

## License
Apache License