package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Element;

public class Panel extends Component {
    private Component content;
    private String heading;

    @Override public Component with(Component child) {
        this.content = child;
        return super.with(child);
    }

    @Override protected void addTo(Component parent) {
        Element panel = parent.element.appendElement("div").addClass("panel panel-default");
        if (heading != null)
            panel.appendElement("div").addClass("panel-heading")
                 .appendElement("h3").addClass("panel-title").text(heading);
        // panel.appendElement("div").addClass("panel-body");
        this.element = panel;
        content.addTo(this);
    }

    public Component heading(String heading) {
        this.heading = heading;
        return this;
    }
}
