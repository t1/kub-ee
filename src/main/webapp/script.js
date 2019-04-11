'use strict';

const DEPLOYMENTS_RESOURCE = baseUri + 'deployments/';
const NO_CONTENT = 204;
const FADE_OUT_TIME = 1000;

class DeploymentMenu extends React.Component {
    render() {
        const versions = (this.props.versions) ?
            this.props.versions.map(version => this.renderVersion(version)) :
            <span className="loading-indicator">Loading...</span>;
        return <ul className="list-unstyled deployment-menu">
            {versions}
            <li>
                <hr/>
            </li>
            <li onClick={() => undeploy(this.props.group)}>
                <span className='icon ion-md-remove-circle version-icon'/>
                undeploy
            </li>
            <li onClick={() => balance(this.props.group)}>
                <span className='icon ion-md-eye version-icon'/>
                balance
            </li>
            <li onClick={() => unbalance(this.props.group)}>
                <span className='icon ion-md-eye-off version-icon'/>
                unbalance
            </li>
        </ul>
    }

    renderVersion(version) {
        return (
            <li key={version.name}
                onClick={() => deploy(this.props.group, version)}
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
    const icon = ((state && state.hover === version.name) ? 'add-circle-outline' : statusIcon(version.status));
    return 'icon ion-md-' + icon + ' version-icon version-icon-' + version.status;
}

function statusIcon(state) {
    switch (state) {
        case 'undeployed':
            return 'remove';
        case 'deployee':
            return 'add-circle-outline';
        case 'deployed':
            return 'checkmark-circle';
        case 'deploying':
            return 'refresh';
        case 'undeployee':
            return 'close-circle-outline';
        case 'undeploying':
            return 'undo';
        case 'removed':
            return 'remove-circle-outline';
    }
}

function click_handler(event) {
    const cellId = $(event.target).parents('.deployment').attr('id');
    if (!cellId)
        return;
    const menu = $id(cellId).find('.versions-menu')[0];

    if (!menu || menu.hasChildNodes())
        return;
    console.debug('menu', menu, cellId);

    ReactDOM.render(
        <DeploymentMenu group={cellId}/>, menu);

    fetchVersions(cellId)
        .then(versions => {
            ReactDOM.render(
                <DeploymentMenu group={cellId} versions={versions.available}/>, menu);
        });
}

function fetchVersions(where) {
    console.debug('fetchVersions', where);

    return fetch(DEPLOYMENTS_RESOURCE + where, {
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

function removeMenu(where) {
    $id(where).find('.versions-menu').children().remove();
}

function deploy(where, version) {
    console.debug('deploy', where, version);

    $id(where).find('.version-name').text(version.name);
    const refreshIcon = cellIcon(where, 'deploying');
    removeMenu(where);

    return post(where, 'mode=deploy&version=' + version.name, refreshIcon, 'deployed', () => {
        refreshIcon.parentNode.removeChild(refreshIcon);
    });
}

function balance(where) {
    console.debug('balance', where);
    const undeployIcon = cellIcon(where, 'balancing');

    return post(where, 'mode=balance', undeployIcon, 'eye', () => {
        // TODO update status
    });
}

function unbalance(where) {
    console.debug('unbalance', where);
    const undeployIcon = cellIcon(where, 'unbalancing');

    return post(where, 'mode=unbalance', undeployIcon, 'eye-off', () => {
        // TODO update status
    });
}

function undeploy(where) {
    console.debug('undeploy', where);
    const undeployIcon = cellIcon(where, 'undeploying');

    return post(where, 'mode=undeploy', undeployIcon, 'removed', () => {
        $id(where).parent().html(undeployedNode(where));
    });
}

function post(where, body, icon, status, faded) {
    return fetch(DEPLOYMENTS_RESOURCE + where, {
        method: 'post',
        headers: {
            'Content-type': 'application/x-www-form-urlencoded; charset=UTF-8',
            'Accept': 'application/json'
        },
        body: body
    })
        .then(response => {
            console.debug('got response', response);
            if (response.status !== NO_CONTENT) {
                response.json().then(json => console.debug("error detail", json));
                throw new Error('unexpected response: ' + response.status);
            }
            icon.className = versionIconClasses({status: status});
        })
        .then(() => {
            $(icon).fadeOut(FADE_OUT_TIME, faded);
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
    if (iconNode.size && iconNode.size() !== 0)
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
    const element = findElement(event);
    const ok = element.hasClass('not-deployed');

    $id('cluster:' + targetClusterId(event)).addClass(ok ? 'drop-ok' : 'drop-not');
    $id('node:' + targetNodeId(event)).addClass(ok ? 'drop-ok' : 'drop-not');
    if (ok) {
        event.preventDefault();
    }
}

function findElement(event) {
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

    const targetElement = findElement(event);
    const targetId = targetElement.attr('id');
    const targetCell = targetElement.parent();
    const operation = op(event);
    const sourceElement = document.getElementById(sourceId);
    const version = $(sourceElement).find('.version-name').text();
    console.debug(operation + ' ' + sourceId + ' -> ' + targetId + ' v' + version);

    const nodeCopy = sourceElement.cloneNode(true);
    nodeCopy.id = targetId;
    replaceChildren(targetCell, nodeCopy);

    switch (operation) {
        case 'copy':
            deploy(targetId, {
                name: version,
                status: 'deploying'
            });
            break;
        case 'move':
            const id = sourceId;
            cellIcon(sourceId, 'undeployee');
            deploy(targetId, {
                name: version,
                status: 'deploying'
            })
                .then(() => {
                    undeploy(id)
                });
            break;
        default:
            throw new Error('undefined drop operation: ' + operation);
    }
}

function undeployedNode(id) {
    const element = document.createElement('div');
    element.className = 'deployment not-deployed';
    element.textContent = ' - ';
    element.id = id;
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

