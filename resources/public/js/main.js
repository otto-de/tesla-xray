function onAcknowledgementClick(xrayUrl, check, environment, minutes) {
    var params = "check-name="+check+"&environment="+environment+"&minutes="+minutes;

    var http = new XMLHttpRequest();
    http.open('POST', xrayUrl+'/acknowledged-checks');
    http.setRequestHeader("Content-type", "application/x-www-form-urlencoded");

    http.onreadystatechange = function()
        {
            if (http.readyState == 4)
            {
                location.reload();
            }
        };
    http.send(params);
}