package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Element;

import javax.ws.rs.core.Link;

public class A extends Component {
    public A(Link link) {
        this.element = new Element("a")
            .attr("rel", link.getRel())
            .attr("href", link.getUri().toString())
            .text(link.getTitle());
    }
}
