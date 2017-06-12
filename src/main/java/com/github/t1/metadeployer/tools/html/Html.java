package com.github.t1.metadeployer.tools.html;

import org.jsoup.nodes.*;

import static java.nio.charset.StandardCharsets.*;

public class Html {
    private static final String BOOTSTRAP_VERSION = "3.2.0";
    private static final String BOOTSTRAP_CDN = "http://maxcdn.bootstrapcdn.com/bootstrap/";
    public static final String BOOTSTRAP_URI = BOOTSTRAP_CDN + BOOTSTRAP_VERSION + "/css/bootstrap.min.css";

    private final Document html;
    private Element body;

    public Html() {
        html = Document.createShell("");
        html.charset(UTF_8);
    }

    @Override public String toString() { return "<!DOCTYPE html>\n" + html.outerHtml(); }


    public void header(String title) {
        title(title);
        stylesheets();
    }

    public void title(String title) { html.title(title); }

    public void stylesheets() { stylesheet(BOOTSTRAP_URI).stylesheet("/style.css"); }

    public Html stylesheet(String href) {
        head().appendElement("link")
              .attr("rel", "stylesheet")
              .attr("href", href);
        return this;
    }

    public void script(String src) {
        head().appendElement("script")
              .attr("type", "text/javascript")
              .attr("src", src);
    }

    public Element head() { return html.head(); }

    public Html withoutContainer() {
        this.body = html.body();
        return this;
    }

    public Element body() {
        if (this.body == null)
            this.body = html.body().appendElement("div").addClass("container");
        return this.body;
    }

    public void h1(String title) {
        body().appendElement("h1").addClass("page-header").text(title);
    }

    public List ul() {
        return new List(body());
    }

    public Table table() { return new Table(body()); }

}
