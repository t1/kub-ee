package com.github.t1.metadeployer.tools.html;

import org.jsoup.nodes.*;

import static java.nio.charset.StandardCharsets.*;

public class Html {
    // private static final String BOOTSTRAP_VERSION = "3.3.7";
    // private static final String BOOTSTRAP_BASE = "http://maxcdn.bootstrapcdn.com/bootstrap/" + BOOTSTRAP_VERSION;
    public static final String BOOTSTRAP_BASE = "bootstrap";
    public static final String BOOTSTRAP_CSS_URI = BOOTSTRAP_BASE + "/css/bootstrap.min.css";
    public static final String BOOTSTRAP_JS_URI = BOOTSTRAP_BASE + "/js/bootstrap.min.js";

    // private static final String JQUERY_VERSION = "1.12.4";
    // private static final String JQUERY_BASE = "https://ajax.googleapis.com/ajax/libs/jquery/" + JQUERY_VERSION;
    private static final String JQUERY_BASE = "jquery";
    private static final String JQUERY_JS_URI = JQUERY_BASE + "/jquery.min.js";

    private final Document html;
    private Element body;

    public Html() {
        html = Document.createShell("");
        html.charset(UTF_8);
        html.head().appendElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1");
    }

    @Override public String toString() { return "<!DOCTYPE html>\n" + html.outerHtml(); }


    public void header(String title) {
        title(title);
        stylesheets();
    }

    public void title(String title) { html.title(title); }

    public void stylesheets() { stylesheet(BOOTSTRAP_CSS_URI).stylesheet("../style.css"); }

    public Html stylesheet(String href) {
        head().appendElement("link")
              .attr("rel", "stylesheet")
              .attr("href", href);
        return this;
    }

    public void script(String src) {
        script(src, "text/javascript");
    }

    public void script(String src, String type) {
        rawBody().appendElement("script")
                 .attr("type", type)
                 .attr("src", src);
    }

    public Element head() { return html.head(); }

    public Html fullWidthContainer() {
        this.body = rawBody().appendElement("div").addClass("container-fluid");
        return this;
    }

    public Element body() {
        if (this.body == null)
            this.body = rawBody().appendElement("div").addClass("container");
        return this.body;
    }

    public Element rawBody() { return html.body(); }

    public void h1(String title) {
        body().appendElement("h1").addClass("page-header").text(title);
    }

    public List ul() {
        return new List(body());
    }

    public Table table() { return new Table(body()); }

    public void footer() {
        script(JQUERY_JS_URI);
        script(BOOTSTRAP_JS_URI);
    }
}
