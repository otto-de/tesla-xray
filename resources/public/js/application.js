var forms = document.querySelectorAll('form');

forms.forEach(function (form) {
    form.addEventListener('submit', function (e) {
        e.preventDefault();

        var fields = [];
        var formData = new FormData(e.target);
        for (var pair of formData.entries()) {
            fields.push(pair[0] + '=' + pair[1]);
        }
        var params = fields.join("&");

        var oReq = new XMLHttpRequest();
        oReq.addEventListener("load", successListener);
        oReq.addEventListener("error", errorListener);
        oReq.open(e.target.dataset.method, e.target.action, true);
        oReq.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
        oReq.send(params);
    });
});

function successListener() {
    location.reload();
}

function errorListener() {
    console.error(this.responseText);
}

function deleteAcknowledgement(xrayUrl, check, environment) {
    console.log("HELLO I WAS CALLED");
    var http = new XMLHttpRequest();
    console.log(xrayUrl + '/acknowledged-checks/'+ check+ '/'+environment);
    http.open('DELETE', xrayUrl + '/acknowledged-checks/'+ check+ '/'+environment);
    http.setRequestHeader("Content-type", "application/x-www-form-urlencoded");

    http.onreadystatechange = function () {
        if (http.readyState == 4) {
            location.reload();
        }
    };
    http.send();
}