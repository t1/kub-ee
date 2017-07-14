package com.github.t1.metadeployer.tools.html;

public class Jumbotron extends Component {
    private Component content;

    @Override public Component with(Component child) {
        this.content = child;
        return super.with(child);
    }

    @Override protected void addTo(Component parent) {
        this.element = parent.element.appendElement("div").addClass("jumbotron");
        content.addTo(this);
    }
}
