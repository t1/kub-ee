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

let sourceId;
let sourceGav;

function escapeColons(text) {
    return text.split(':').join('\\:');
}

function sourceParent() {
    return $(escapeColons('#' + sourceId)).parent();
}

function drag_start(event) {
    sourceId = event.currentTarget.id;
    sourceGav = event.currentTarget.title;
    event.dataTransfer.effectAllowed = "copyMove";
    event.dataTransfer.setData('text', event.currentTarget.className + ':' + sourceId + '@' + sourceGav);
    sourceParent().addClass('drag-source');
}

function targetClusterId(event) {
    const id = dragTargetId(event);
    if (!id) return id;
    return cluster(id);
}

function cluster(targetId) {
    const split = targetId.split(':');
    return split[0] + ':' + split[1];
}

function targetNodeId(event) {
    const id = dragTargetId(event);
    if (!id) return id;
    return node(id);
}

function node(id) {
    const split = id.split(':');
    return split[2] + ':' + split[3];
}

function dragTargetId(event) {
    return $(event.currentTarget).find('div.deployment').attr('id');
}

function clusterHeader(clusterId) {
    const selector = escapeColons('#cluster:' + clusterId);
    const selected = $(selector);
    return (clusterId) ? selected : undefined;
}

function nodeHeader(nodeId) {
    return (nodeId) ? $(escapeColons('#node:' + nodeId)) : undefined;
}

function isSourceCluster(clusterId) {
    return sourceId.startsWith(clusterId + ":");
}

function isSourceNode(nodeId) {
    const split = sourceId.split(':');
    return (split[2] + ':' + split[3]) === nodeId;
}

function drag_enter(event) {
    if (!isSourceCluster(targetClusterId(event)) || !isSourceNode(targetNodeId(event))) {
        event.preventDefault();
    }
}

function drag_over(event) {
    const dropCluster = targetClusterId(event);
    const sameCluster = isSourceCluster(dropCluster);
    clusterHeader(dropCluster).addClass(sameCluster ? 'drop-not' : 'drop-ok');

    const dropNode = targetNodeId(event);
    const sameNode = isSourceNode(dropNode);
    nodeHeader(dropNode).addClass(sameNode ? 'drop-not' : 'drop-ok');

    if (sameCluster && sameNode) {
        // default -> don't drop
    } else {
        event.preventDefault();
    }
}

function clearDropZones(event) {
    const id = dragTargetId(event);
    if (id) {
        clusterHeader(cluster(id)).removeClass('drop-not').removeClass('drop-ok');
        nodeHeader(node(id)).removeClass('drop-not').removeClass('drop-ok');
    }
}

function drag_leave(event) {
    event.preventDefault();
    clearDropZones(event);
}

function drag_end(event) {
    sourceParent().removeClass('drag-source');
    sourceId = undefined;
    sourceGav = undefined;
}

function drop_handler(event) {
    sourceParent().removeClass('drag-source');
    clearDropZones(event);
    event.preventDefault();
    const targetId = dragTargetId(event);
    const operation = op(event);
    const node = document.getElementById(sourceId);
    console.debug(operation + ' ' + sourceGav + ' ' + sourceId + ' -> ' + targetId);
    switch (operation) {
        case 'copy':
            const nodeCopy = node.cloneNode(true);
            nodeCopy.id = 'newId';
            event.target.appendChild(nodeCopy);
            break;
        case 'move':
            event.target.appendChild(node);
            break;
        default:
            throw new Error('undefined drop operation: ' + operation);
    }
}

function op(event) {
    return (event.dataTransfer.dropEffect === 'none')
        ? (event.dataTransfer.effectAllowed === 'all') ? 'move' : event.dataTransfer.effectAllowed
        : event.dataTransfer.dropEffect;
}
