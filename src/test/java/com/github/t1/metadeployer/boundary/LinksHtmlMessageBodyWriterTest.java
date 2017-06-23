package com.github.t1.metadeployer.boundary;

import org.glassfish.jersey.uri.internal.JerseyUriBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import javax.ws.rs.core.UriInfo;
import java.io.*;
import java.net.URI;
import java.util.Map;

import static com.github.t1.metadeployer.tools.html.Html.*;
import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LinksHtmlMessageBodyWriterTest {
    private static final URI BASE_URI = URI.create("http://localhost:8080/meta-deployer/api");
    private LinksHtmlMessageBodyWriter writer = new LinksHtmlMessageBodyWriter();

    private Map<String, URI> getLinks() {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getBaseUriBuilder()).then(i -> new JerseyUriBuilder().uri(BASE_URI));
        return new Boundary().getLinks(uriInfo);
    }

    @Test
    public void shouldWriteHtml() throws Exception {
        Map<String, URI> links = getLinks();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(links, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Index");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
                .extracting(element -> element.attr("href"))
                .containsOnly(BOOTSTRAP_BASE + "/css/bootstrap.min.css", "../style.css");
        assertThat(html.body().html()).isEqualTo(""
                + "<div class=\"container\"> \n"
                + " <h1 class=\"page-header\">Links</h1> \n"
                + " <ul class=\"list-group\"> \n"
                + "  <li class=\"list-group-item\"><a href=\"" + BASE_URI + "/clusters\">clusters</a></li> \n"
                + "  <li class=\"list-group-item\"><a href=\"" + BASE_URI + "/slots\">slots</a></li> \n"
                + "  <li class=\"list-group-item\"><a href=\"" + BASE_URI + "/stages\">stages</a></li> \n"
                + "  <li class=\"list-group-item\"><a href=\"" + BASE_URI + "/deployments\">deployments</a></li> \n"
                + " </ul> \n"
                + "</div>");
    }
}
