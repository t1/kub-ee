package com.github.t1.metadeployer.tools.html;

import org.jsoup.nodes.Element;

public class List {
    private final Element list;

    public List(Element container) {
        this.list = container.appendElement("ul").addClass("list-group");
    }

    public Element li() {
        return list.appendElement("li").addClass("list-group-item");
    }
}
