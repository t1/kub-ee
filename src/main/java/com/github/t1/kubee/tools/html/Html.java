package com.github.t1.kubee.tools.html;

import org.jsoup.nodes.Document;

import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;

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
    private Path baseUri;

    public Html() {
        html = Document.createShell("");
        html.charset(UTF_8);
        html.head().appendElement("meta")
            .attr("name", "viewport")
            .attr("content", "width=device-width, initial-scale=1");
    }

    @Override public String toString() { return "<!DOCTYPE html>\n" + html.outerHtml(); }


    public void baseUri(String baseUri) { this.baseUri = Paths.get(baseUri); }

    public void header(String title) {
        title(title);
        stylesheets();
    }

    public void title(String title) { html.title(title); }

    public void stylesheets() { stylesheet(BOOTSTRAP_CSS_URI).stylesheet("../style.css"); }

    public Html stylesheet(String href) {
        if (baseUri != null)
            href = baseUri.resolve(href).normalize().toString();
        html.head().appendElement("link")
            .attr("rel", "stylesheet")
            .attr("href", href);
        return this;
    }

    public void script(String src) {
        script(src, "text/javascript");
    }

    public void script(String src, String type) {
        html.body().appendElement("script")
            .attr("type", type)
            .attr("src", src);
    }

    public void inlineScript(String text) {
        html.body().appendElement("script").text(text);
    }

    public Container container() { return new Container(html.body()); }

    public void footer() {
        script("popper.js/umd/popper.js");
        script(JQUERY_JS_URI);
        script(BOOTSTRAP_JS_URI);
    }
}
