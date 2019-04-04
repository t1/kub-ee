package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Element;

public class CustomComponent extends Component {
    public static CustomComponent span() { return new CustomComponent("span"); }

    public static CustomComponent div() { return new CustomComponent("div"); }

    public CustomComponent(String tag) { this.element = new Element(tag); }

    public CustomComponent className(String className) {
        element.addClass(className);
        return this;
    }

    public CustomComponent id(String id) { return attr("id", id); }

    public CustomComponent attr(String name, String value) {
        element.attr(name, value);
        return this;
    }

    public CustomComponent html(String html) {
        element.html(html);
        return this;
    }

    public CustomComponent text(String text) {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (i > 0)
                element.appendElement("br");
            element.appendText(lines[i]);
        }
        return this;
    }

    @Override public CustomComponent with(Component child) {
        child.addTo(this);
        return this;
    }

    @Override protected void addTo(Component parent) {
        parent.element.appendChild(this.element);
    }
}
