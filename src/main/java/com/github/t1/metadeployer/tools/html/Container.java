package com.github.t1.metadeployer.tools.html;

import org.jsoup.nodes.Element;

public class Container extends Component {
    private boolean fluid;

    public Container(Element element) { this.element = element.appendElement("div"); }

    @Override public Component with(Component child) {
        super.with(child);
        element.addClass(fluid ? "container-fluid" : "container");
        child.addTo(this);
        return this;
    }

    public Container fluid() {
        this.fluid = true;
        return this;
    }
}
