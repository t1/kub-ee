package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Element;

public class Container extends Component {
    private boolean fluid;

    Container(Element element) { this.element = element.appendElement("div"); }

    @Override public Component with(Component child) {
        element.addClass(fluid ? "container-fluid" : "container");
        child.addTo(this);
        return this;
    }

    public Container fluid() {
        this.fluid = true;
        return this;
    }
}
