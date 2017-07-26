'use strict';

const deploymentsResource = baseUri + 'deployments/';
const NO_CONTENT = 204;

class DeploymentMenu extends React.Component {
    render() {
        const versions = (this.props.versions)
            ? this.props.versions.map(version => this.renderVersion(version))
            : <span className="loading-indicator">Loading...</span>;
        return <ul className="list-unstyled deployment-menu">
            {versions}
            <li>
                <hr/>
            </li>
            <li onClick={() => undeploy(this.props.group)}>
                <span className='glyphicon glyphicon-ban-circle version-icon'/>
                undeploy
            </li>
        </ul>
    }

    renderVersion(version) {
        return (
            <li key={version.name}
                onClick={() => deployVersion(this.props.group, version)}
                onMouseEnter={() => this.hover(version)}
                onMouseLeave={() => this.hover(undefined)}
            >
                <span className={versionIconClasses(version, this.state)}/>
                <span className="version">{version.name}</span>
            </li>
        );
    }

    hover(version) {
        this.setState({
            hover: (version && 'undeployed' === version.status) ? version.name : undefined
        });
    };
}

function versionIconClasses(version, state) {
    const icon = ((state && state.hover === version.name) ? 'ok-sign' : statusIcon(version.status));
    return 'glyphicon glyphicon-' + icon + ' version-icon version-icon-' + version.status;
}

function statusIcon(state) {
    switch (state) {
        case 'undeployed':
            return 'minus';
        case 'deployee':
            return 'refresh';
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

function click_handler(event) {
    const cellId = $(event.target).parents('.deployment').attr('id');
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

function deployVersion(where, version, other) {
    console.debug('deployVersion', where, version, other);

    const refreshIcon = cellIcon(where, 'deployee');

    fetch(deploymentsResource + where, {
        method: 'post',
        headers: {
            'Content-type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'Accept': 'application/json'
        },
        body: 'version=' + version.name + ((other) ? '&' + other : '')
    })
        .then(response => {
            console.debug('got response', response);
            if (response.status !== NO_CONTENT)
                throw new Error('unexpected response: ' + response.status);
            refreshIcon.className = versionIconClasses({status: 'deployed'});
        })
        .catch(error => {
            console.debug('failed', error);
        });
}

function undeploy(where) {
    console.debug('undeploy', where);
    const undeployIcon = cellIcon(where, 'undeployee');

    fetch(deploymentsResource + where, {
        method: 'post',
        headers: {
            'Content-type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'Accept': 'application/json'
        },
        body: 'remove=' + where
    })
        .then(response => {
            console.debug('got response', response);
            if (response.status !== NO_CONTENT)
                throw new Error('unexpected response: ' + response.status);
            undeployIcon.className = versionIconClasses({status: 'undeployed'});
        })
        .catch(error => {
            console.debug('failed', error);
        });
}

function cellIcon(id, status) {
    const parent = $id(id).find('.dropdown > .dropdown-toggle');
    let icon = getOrCreateCellIcon(parent);
    icon.className = versionIconClasses({status: status});
    return icon;
}

function getOrCreateCellIcon(parent) {
    const iconNode = parent.find('.version-icon');
    if (iconNode.size() !== 0)
        return iconNode[0];
    const icon = document.createElement('span');
    parent.append(icon);
    return icon;
}

function $id(selector) {
    return $('#' + selector.split(':').join('\\:'));
}


// =======================================
// see https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API
// TODO replace with https://github.com/bevacqua/dragula

let sourceId;

function drag_start(event) {
    sourceId = event.currentTarget.id;
    event.dataTransfer.effectAllowed = "copyMove";
    event.dataTransfer.setData('text', event.currentTarget.className + ':' + sourceId);
    $id(sourceId).parent().addClass('drag-source');
}

function targetClusterId(event) {
    const id = dragTargetId(event);
    if (!id) return id;
    return cluster(id);
}

function targetNodeId(event) {
    const id = dragTargetId(event);
    if (!id) return id;
    return node(id);
}

function cluster(targetId) {
    const split = targetId.split(':');
    return split[0] + ':' + split[1];
}

function node(id) {
    const split = id.split(':');
    return split[2] + ':' + split[3];
}

function dragTargetId(event) {
    return $(event.currentTarget).find('div.deployment').attr('id');
}

function drag_over(event) {
    const e = dropElement(event);
    const ok = e.size() > 0 && e.hasClass('not-deployed');

    $id('cluster:' + targetClusterId(event)).addClass(ok ? 'drop-ok' : 'drop-not');
    $id('node:' + targetNodeId(event)).addClass(ok ? 'drop-ok' : 'drop-not');
    if (ok) {
        event.preventDefault();
    }
}

function dropElement(event) {
    const dropClusterId = targetClusterId(event);
    const dropNodeId = targetNodeId(event);
    const appName = sourceId.split(':')[4];
    return $id(dropClusterId + ':' + dropNodeId + ':' + appName);
}

function drag_leave(event) {
    event.preventDefault();
    removeDropZoneStyles(event);
}

function removeDropZoneStyles(event) {
    const id = dragTargetId(event);
    if (id) {
        $id('cluster:' + cluster(id)).removeClass('drop-not').removeClass('drop-ok');
        $id('node:' + node(id)).removeClass('drop-not').removeClass('drop-ok');
    }
}

function removeDragSourceStyle() {
    $id(sourceId).parent().removeClass('drag-source');
}

function drag_end() {
    removeDragSourceStyle();
    sourceId = undefined;
}

function drop_handler(event) {
    removeDragSourceStyle();
    removeDropZoneStyles(event);
    event.preventDefault();

    const targetElement = dropElement(event);
    const targetId = targetElement.attr('id');
    const targetCell = targetElement.parent();
    const operation = op(event);
    const sourceElement = document.getElementById(sourceId);
    const version = $(sourceElement).find('.version-name').text();
    console.debug(operation + ' ' + sourceId + ' -> ' + targetId + ' v' + version);
    let other = '';

    switch (operation) {
        case 'copy':
            const nodeCopy = sourceElement.cloneNode(true);
            nodeCopy.id = targetId;
            replaceChildren(targetCell, nodeCopy);
            break;
        case 'move':
            const sourceParentElement = $id(sourceId).parent();
            sourceElement.id = targetId;
            replaceChildren(targetCell, sourceElement);
            sourceParentElement.append(undeployedNode());
            other = 'remove=' + sourceId;
            break;
        default:
            throw new Error('undefined drop operation: ' + operation);
    }
    deployVersion(targetId, {name: version, status: 'deployee'}, other);
}

function undeployedNode() {
    const element = document.createElement('div');
    element.className = 'deployment not-deployed';
    element.textContent = ' - ';
    element.id = sourceId;
    return element;
}

function op(event) {
    return (event.dataTransfer.dropEffect === 'none')
        ? (event.dataTransfer.effectAllowed === 'all') ? 'move' : event.dataTransfer.effectAllowed
        : event.dataTransfer.dropEffect;
}

function replaceChildren(parent, child) {
    parent.children().remove();
    parent.append(child);
}

