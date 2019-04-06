package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.model.ReverseProxy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;

import static com.github.t1.kubee.tools.html.Html.BOOTSTRAP_CSS;
import static com.github.t1.kubee.tools.html.Html.ICONS_CSS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class ReverseProxyListHtmlMessageBodyWriterTest {
    private final ReverseProxyListHtmlMessageBodyWriter writer = new ReverseProxyListHtmlMessageBodyWriter();

    @Test void shouldWriteHtml() throws Exception {
        List<ReverseProxy> reverseProxies = asList(
            ReverseProxy.builder().from(URI.create("http://from1")).to(1).build(),
            ReverseProxy.builder().from(URI.create("http://from2")).build(),
            ReverseProxy.builder().from(URI.create("http://from3")).to(3).build()
        );
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(reverseProxies, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Reverse Proxies");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
            .extracting(element -> element.attr("href"))
            .containsOnly(BOOTSTRAP_CSS, ICONS_CSS, "../style.css");
        assertThat(html.body().html()).isEqualTo(""
            + "<div class=\"container\"> \n"
            + " <div class=\"panel panel-default\"> \n"
            + "  <div class=\"panel-heading\"> \n"
            + "   <h3 class=\"panel-title\">Reverse Proxies</h3> \n"
            + "  </div> \n"
            + "  <div class=\"table-responsive\"> \n"
            + "   <table class=\"table table-striped\"> \n"
            + "    <tbody> \n"
            + "     <tr> \n"
            + "      <th>From</th> \n"
            + "      <th>To</th> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td>http://from1</td> \n"
            + "      <td>1</td> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td>http://from2</td> \n"
            + "      <td>-</td> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td>http://from3</td> \n"
            + "      <td>3</td> \n"
            + "     </tr> \n"
            + "    </tbody> \n"
            + "   </table> \n"
            + "  </div> \n"
            + " </div> \n"
            + "</div>");
    }
}
