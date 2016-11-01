var acknowledgementInputValueId = "acknowledgement-input-value";

function executeWithAcknowledgementValue(fn) {
    var inputValue = document.getElementById(acknowledgementInputValueId);
    if (inputValue != null) {
        fn(inputValue.innerHTML)
    } else {
        console.error("No element found for id " + acknowledgementInputValueId);
    }
}

function onAcknowledgementClick(xrayUrl, check, environment) {
    executeWithAcknowledgementValue(function (acknowledgementValue) {
        var params = "check-name=" + check + "&environment=" + environment + "&hours=" + acknowledgementValue;

        var http = new XMLHttpRequest();
        http.open('POST', xrayUrl + '/acknowledged-checks');
        http.setRequestHeader("Content-type", "application/x-www-form-urlencoded");

        http.onreadystatechange = function () {
            if (http.readyState == 4) {
                location.reload();
            }
        };
        http.send(params);
    });
}

function onAcknowledgementDecrease() {
    executeWithAcknowledgementValue(function (acknowledgementValue) {
        var hours = parseInt(acknowledgementValue);
        var inputValue = document.getElementById(acknowledgementInputValueId);
        inputValue.innerHTML = "" + (hours - 1);
    });
}

function onAcknowledgementIncrease() {
    executeWithAcknowledgementValue(function (acknowledgementValue) {
        var hours = parseInt(acknowledgementValue);
        var inputValue = document.getElementById(acknowledgementInputValueId);
        inputValue.innerHTML = "" + (hours + 1);
    });
}