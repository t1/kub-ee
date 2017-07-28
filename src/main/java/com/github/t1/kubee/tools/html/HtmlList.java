package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Element;

public class HtmlList extends Component {
    @Override protected void addTo(Component parent) {
        this.element = parent.element.appendElement("ul").addClass("list-group");
    }

    public Element li() {
        return this.element.appendElement("li").addClass("list-group-item");
    }
}
