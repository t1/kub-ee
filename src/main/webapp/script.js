'use strict';

function click_handler(event) {
    const form = event.target.parentNode.getElementsByTagName('form')[0];

    if (!form || form.hasChildNodes())
        return;
    const cellId = event.target.parentNode.parentNode.id;
    console.debug("form", form, cellId);

    const versions = ['1.2.3', '1.2.4', '1.2.5'];

    versions.forEach(version => {
        let div = document.createElement('div');
        $(div).addClass("radio");
        form.appendChild(div);

        const input = document.createElement('input');
        $(input).attr('type', 'radio')
            .attr('name', cellId)
            .val(version)
            .attr('onclick', 'selectVersion(event.target.name, event.target.value);');

        const label = document.createElement('label');
        label.appendChild(input);
        label.appendChild(document.createTextNode(version));
        div.appendChild(label);
    });
}

function fetchVersions(where) {
    console.debug("selectVersion", where);

    fetch('http://localhost:8080/meta-deployer/api/deployments/' + where, {
        method: 'get',
        headers: {
            "Content-type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept": "application/json"
        },
        body: 'where=' + where
    })
        .then(response => {
            if (response.ok) return response.json();
            else return new Error(response);
        })
        .then(data => {
            console.log('success', data);
        })
        .catch(error => {
            console.log('failed', error);
        });
}

function selectVersion(where, version) {
    console.debug("selectVersion", where, version);

    fetch('http://localhost:8080/meta-deployer/api/deployments/' + where, {
        method: 'post',
        headers: {
            "Content-type": "application/x-www-form-urlencoded; charset=UTF-8",
            "Accept": "application/json"
        },
        body: 'where=' + where + '&version=' + version
    })
        .then(response => {
            if (response.ok) return response.json();
            else return new Error(response);
        })
        .then(data => {
            console.log('success', data);
        })
        .catch(error => {
            console.log('failed', error);
        });
}

// =======================================
// see https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API

function drag_start(ev) {
    ev.currentTarget.style.border = "dashed";
    ev.dataTransfer.setData("text", ev.target.id);
}

function drag_enter(ev) {
    ev.preventDefault();
    ev.currentTarget.style.background = "lightblue";
}

function drag_over(ev) {
    ev.preventDefault();
}

function drag_leave(ev) {
    ev.preventDefault();
    ev.currentTarget.style.background = null;
}

function drag_end(ev) {
    ev.preventDefault();
    ev.target.style.border = null;
    ev.dataTransfer.clearData();
}

function drop_handler(ev) {
    ev.preventDefault();
    const id = ev.dataTransfer.getData("text");
    if (ev.dataTransfer.effectAllowed === "copy") {
        console.log("copy " + id + "->" + ev.target.id);
        const nodeCopy = document.getElementById(id).cloneNode(true);
        nodeCopy.id = "newId";
        ev.target.appendChild(nodeCopy);
    } else {
        console.log("move " + id + "->" + ev.target.id);
        ev.target.appendChild(document.getElementById(id));
    }
}
