package com.github.t1.metadeployer.boundary;

import org.jsoup.nodes.*;

import static java.nio.charset.StandardCharsets.*;

public class AbstractHtml {
    private Document html = Document.createShell("");

    public AbstractHtml() { html.charset(UTF_8); }

    protected void header(String title) {
        title(title);
        stylesheets();
    }

    protected void title(String title) { html.title(title); }

    protected void stylesheets() {
        stylesheet("http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css");
        stylesheet("/style.css");
    }

    protected void stylesheet(String href) {
        head().appendElement("link")
              .attr("rel", "stylesheet")
              .attr("href", href);
    }

    protected void script(String src) {
        head().appendElement("script")
              .attr("type", "text/javascript")
              .attr("src", src);
    }

    protected Element head() { return html.head(); }

    protected Element body() { return html.body(); }

    @Override public String toString() { return "<!DOCTYPE html>\n" + html.outerHtml(); }

    protected Element table() {
        return body()
                .appendElement("div").addClass("table-responsive")
                .appendElement("table").addClass("table table-striped")
                .appendElement("tbody");
    }
}
