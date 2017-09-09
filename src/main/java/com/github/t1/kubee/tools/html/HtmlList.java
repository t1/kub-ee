package com.github.t1.kubee.tools.html;

public class HtmlList extends Component {
    @Override protected void addTo(Component parent) {
        this.element = parent.element.appendElement("ul").addClass("list-group");
    }

    public HtmlList withItem(A a) {
        this.element.appendElement("li").addClass("list-group-item").appendChild(a.element);
        return this;
    }
}
