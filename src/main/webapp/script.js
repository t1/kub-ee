'use strict';

const deploymentsResource = baseUri + 'deployments/';

class DeploymentMenu extends React.Component {
    render() {
        const versions = (this.props.versions)
            ? this.props.versions.map(version => this.renderVersion(version))
            : <span className="loading-indicator">Loading...</span>;
        return <ul className="list-unstyled">{versions}</ul>
    }

    renderVersion(version) {
        return (
            <li key={version.name}
                onClick={() => selectVersion(this.props.group, version)}
                onMouseEnter={() => this.hover(version)}
                onMouseLeave={() => this.hover(undefined)}
            >
                <span className={this.versionIconClasses(version)}/>
                <span className="version">{version.name}</span>
            </li>
        );
    }

    hover(version) {
        const hover = (version && 'undeployed' === version.status) ? version.name : undefined;
        this.setState({
            hover: hover
        });
    };

    versionIconClasses(version) {
        const icon = ((this.state && this.state.hover === version.name) ? 'ok-sign' : this.icon(version.status));
        return 'glyphicon glyphicon-' + icon + ' version-icon version-icon-' + version.status;
    }

    icon(state) {
        switch (state) {
            case 'undeployed':
                return 'minus';
            case 'deployee':
                return 'ok-circle';
            case 'deployed':
                return 'ok-circle';
            case 'deploying':
                return 'refresh';
            case 'undeployee':
                return 'refresh';
            case 'undeploying':
                return 'refresh';
        }
    }
}

function click_handler(event) {
    const cellId = event.target.parentNode.parentNode.id;
    if (!cellId)
        return;
    const idSelector = (className) => '#' + cellId.split(':').join('\\:') + ' .' + className;
    const menu = $(idSelector('versions-menu'))[0];

    if (!menu || menu.hasChildNodes())
        return;
    console.debug('menu', menu, cellId);

    let rot = 0;

    function animate() {
        rot += 360;
        if (rot > 360000)
            rot -= 360000;
        function transform(now, undeploying) {
            let transform = '';
            if (undeploying)
                transform += 'scale(-1, 1) translatex(calc(100% - 20px)) ';
            transform += 'rotate(' + now + 'deg)';
            return transform;
        }

        $(idSelector('version-icon-undeploying') + ', ' + idSelector('version-icon-deploying'))
            .animate(
                {rotation: rot},
                {
                    duration: 2000,
                    easing: 'linear',
                    step: function (now) {
                        const undeploying = this.attributes['class'].value.indexOf('version-icon-undeploying') >= 0;
                        $(this).css({transform: transform(now, undeploying)});
                    },
                    complete: animate
                }
            );
    }

    ReactDOM.render(<DeploymentMenu group={cellId}/>, menu);

    fetchVersions(cellId)
        .then(versions => {
            ReactDOM.render(<DeploymentMenu group={cellId} versions={versions.available}/>, menu);
        })
        .then(animate);
}

function fetchVersions(where) {
    console.debug('fetchVersion', where);

    return fetch(deploymentsResource + where, {
        method: 'get',
        headers: {
            'Accept': 'application/json'
        }
    })
        .then(response => {
            if (response.ok) return response.json();
            else throw new Error(response);
        })
        .then(data => {
            console.debug('got versions', data);
            return data;
        })
        .catch(error => {
            console.debug('failed', error);
        });
}

function selectVersion(where, version) {
    console.debug('selectVersion', where, version);

    fetch(deploymentsResource + where, {
        method: 'post',
        headers: {
            'Content-type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'Accept': 'application/json'
        },
        body: 'version=' + version.name
    })
        .then(response => {
            if (response.ok) return response.json();
            else return new Error(response);
        })
        .then(data => {
            console.debug('posted select', data);
        })
        .catch(error => {
            console.debug('failed', error);
        });
}

// =======================================
// see https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API

function drag_start(ev) {
    ev.currentTarget.style.border = 'dashed';
    ev.dataTransfer.setData('text', ev.target.id);
}

function drag_enter(ev) {
    ev.preventDefault();
    ev.currentTarget.style.background = 'lightblue';
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
    const id = ev.dataTransfer.getData('text');
    if (ev.dataTransfer.effectAllowed === 'copy') {
        console.debug('copy ' + id + '->' + ev.target.id);
        const nodeCopy = document.getElementById(id).cloneNode(true);
        nodeCopy.id = 'newId';
        ev.target.appendChild(nodeCopy);
    } else {
        console.debug('move ' + id + '->' + ev.target.id);
        ev.target.appendChild(document.getElementById(id));
    }
}
