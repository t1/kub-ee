package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.model.ReverseProxy;
import com.github.t1.kubee.model.ReverseProxy.Location;
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
            ReverseProxy.builder().from(URI.create("http://from1"))
                .location(Location.builder().fromPath("a").target(URI.create("http://to1")).build())
                .location(Location.builder().fromPath("b").target(URI.create("http://to2")).build())
                .build(),
            ReverseProxy.builder().from(URI.create("http://from2")).build(),
            ReverseProxy.builder().from(URI.create("http://from3"))
                .location(Location.builder().fromPath("c").target(URI.create("http://to3")).build())
                .build()
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
            + "      <th>Path</th> \n"
            + "      <th>Target</th> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td rowspan=\"2\">http://from1</td> \n"
            + "      <td>a</td> \n"
            + "      <td>http://to1</td> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td>b</td> \n"
            + "      <td>http://to2</td> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td rowspan=\"0\">http://from2</td> \n"
            + "      <td>-</td> \n"
            + "      <td>-</td> \n"
            + "     </tr> \n"
            + "     <tr> \n"
            + "      <td rowspan=\"1\">http://from3</td> \n"
            + "      <td>c</td> \n"
            + "      <td>http://to3</td> \n"
            + "     </tr> \n"
            + "    </tbody> \n"
            + "   </table> \n"
            + "  </div> \n"
            + " </div> \n"
            + "</div>");
    }
}
