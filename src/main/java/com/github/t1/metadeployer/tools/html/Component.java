package com.github.t1.metadeployer.tools.html;

import org.jsoup.nodes.Element;

public class Component {
    protected Element element;

    public Component with(Component child) { return this; }

    protected void addTo(Component parent) {}
}
