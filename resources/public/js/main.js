function onAcknowledgementClick(xrayUrl, check, minutes) {
    var params = "check-name="+check+"&minutes="+minutes;

    var http = new XMLHttpRequest();
    http.open('POST', xrayUrl+'/acknowledged-checks');
    http.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    http.send(params);
    location.reload();
}