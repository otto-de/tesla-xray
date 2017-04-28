var forms = document.querySelectorAll('form');

forms.forEach(function (form) {
    console.log("registering submit handler", form);
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