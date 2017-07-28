package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.boundary.BoundaryFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import javax.ws.rs.core.Link;
import java.io.*;
import java.net.URI;
import java.util.List;

import static com.github.t1.kubee.tools.html.Html.*;
import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.*;

public class LinkListHtmlMessageBodyWriterTest {
    private static final URI BASE_URI = URI.create("http://localhost:8080/kub-ee/api");
    private LinkListHtmlMessageBodyWriter writer = new LinkListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<Link> links = BoundaryFactory.createWithBaseUri(BASE_URI).getLinks();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(links, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Index");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
                .extracting(element -> element.attr("href"))
                .containsOnly("api/" + BOOTSTRAP_BASE + "/css/bootstrap.min.css", "style.css");
        assertThat(html.body().html()).isEqualTo(""
                + "<div class=\"container\"> \n"
                + " <h1 class=\"page-header\">Links</h1> \n"
                + " <ul class=\"list-group\"> \n"
                + "  <li class=\"list-group-item\"><a rel=\"load-balancers\" href=\""
                + BASE_URI + "/load-balancers\">Load Balancers</a></li> \n"
                + "  <li class=\"list-group-item\"><a rel=\"reverse-proxies\" href=\""
                + BASE_URI + "/reverse-proxies\">Reverse Proxies</a></li> \n"
                + "  <li class=\"list-group-item\"><a rel=\"clusters\" href=\""
                + BASE_URI + "/clusters\">Clusters</a></li> \n"
                + "  <li class=\"list-group-item\"><a rel=\"slots\" href=\""
                + BASE_URI + "/slots\">Slots</a></li> \n"
                + "  <li class=\"list-group-item\"><a rel=\"stages\" href=\""
                + BASE_URI + "/stages\">Stages</a></li> \n"
                + "  <li class=\"list-group-item\"><a rel=\"deployments\" href=\""
                + BASE_URI + "/deployments\">Deployments</a></li> \n"
                + " </ul> \n"
                + "</div>");
    }
}
