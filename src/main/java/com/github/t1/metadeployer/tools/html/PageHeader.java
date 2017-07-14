package com.github.t1.metadeployer.tools.html;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class PageHeader extends Component {
    private final String title;

    @Override protected void addTo(Component parent) {
        this.element = parent.element.appendElement("h1").addClass("page-header").text(title);
    }
}
