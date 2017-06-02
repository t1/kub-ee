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

function drop_handler(ev) {
    ev.preventDefault();
    var id = ev.dataTransfer.getData("text");
    if (ev.dataTransfer.effectAllowed === "copy") {
        console.log("copy " + id + "->" + ev.target.id);
        var nodeCopy = document.getElementById(id).cloneNode(true);
        nodeCopy.id = "newId";
        ev.target.appendChild(nodeCopy);
    } else {
        console.log("move " + id + "->" + ev.target.id);
        ev.target.appendChild(document.getElementById(id));
    }
}

function drag_end(ev) {
    ev.preventDefault();
    ev.target.style.border = null;
    ev.dataTransfer.clearData();
}
