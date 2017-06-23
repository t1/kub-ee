'use strict';

function Version(props) {
    return (
        <div className="radio">
            <label><input type="radio" name={props.group} value={props.value}
                          onClick={props.onClick}/>{props.value}</label>
        </div>
    );
}

class DeploymentMenu extends React.Component {
    render() {
        const group = this.props.group;
        const versions = this.props.versions.map(version => {
            return (
                <div key={version}>
                    <Version value={version} group={group} onClick={() => selectVersion(group, version)}/>
                </div>
            );
        });
        return <div>{versions}</div>
    }
}

function click_handler(event) {
    const form = event.target.parentNode.getElementsByTagName('form')[0];

    if (!form || form.hasChildNodes())
        return;
    const cellId = event.target.parentNode.parentNode.id;
    console.debug("form", form, cellId);

    fetchVersions(cellId).then(versions => {
        ReactDOM.render(<DeploymentMenu group={cellId} versions={versions}/>, form);
    });
}

function fetchVersions(where) {
    console.debug("fetchVersion", where);

    return fetch('http://localhost:8080/meta-deployer/api/deployments/' + where, {
        method: 'get',
        headers: {
            "Accept": "application/json"
        }
    })
        .then(response => {
            if (response.ok) return response.json();
            else return new Error(response);
        })
        .then(data => {
            console.debug('success', data);
            return data;
        })
        .catch(error => {
            console.debug('failed', error);
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
            console.debug('success', data);
        })
        .catch(error => {
            console.debug('failed', error);
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
        console.debug("copy " + id + "->" + ev.target.id);
        const nodeCopy = document.getElementById(id).cloneNode(true);
        nodeCopy.id = "newId";
        ev.target.appendChild(nodeCopy);
    } else {
        console.debug("move " + id + "->" + ev.target.id);
        ev.target.appendChild(document.getElementById(id));
    }
}
