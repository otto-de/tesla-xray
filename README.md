# tesla-xray
tesla-xray is a component for executing and visualizing checks.   
It can be used with the tesla-microservice.  
Checks return Check-Results which currently look like this:

			{:status <ok/error>
			:message <your message>
			:timestamp <a-timestamp>}

Checks can pretty much check/assert anything you like, implementation is up to you.
A unlimited number of custom-written-checks can be registered.
Registered checks are executed in a configureable time-schedule.  
The check-results are stored and are visualized at an endpoint.   

Ideas for further features:  

	* Visualization strategies (e.g. show red-status if a number of checks returned error)
	* App-Status which shows the status of registered checks
	* Graphs for registered checks (e.g. Execution times, status, ...)
	* Statistics on registered checks

## Usage
TBD
