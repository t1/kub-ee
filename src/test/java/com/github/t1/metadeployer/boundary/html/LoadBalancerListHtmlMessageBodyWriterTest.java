package com.github.t1.metadeployer.boundary.html;

import com.github.t1.metadeployer.model.LoadBalancer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.*;
import java.net.URI;
import java.util.List;

import static com.github.t1.metadeployer.tools.html.Html.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class LoadBalancerListHtmlMessageBodyWriterTest {
    private final LoadBalancerListHtmlMessageBodyWriter writer = new LoadBalancerListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<LoadBalancer> loadBalancers = asList(
                LoadBalancer.builder().from(URI.create("http://from1")).to(URI.create("http://to1")).build(),
                LoadBalancer.builder().from(URI.create("http://from2")).to(URI.create("http://to2")).build(),
                LoadBalancer.builder().from(URI.create("http://from3")).to(URI.create("http://to3")).build()
        );
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(loadBalancers, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Load-Balancing");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
                .extracting(element -> element.attr("href"))
                .containsOnly(BOOTSTRAP_BASE + "/css/bootstrap.min.css", "../style.css");
        assertThat(html.body().html()).isEqualTo(""
                + "<div class=\"container\"> \n"
                + " <div class=\"panel panel-default\"> \n"
                + "  <div class=\"panel-heading\"> \n"
                + "   <h3 class=\"panel-title\">Load-Balancing</h3> \n"
                + "  </div> \n"
                + "  <div class=\"table-responsive\"> \n"
                + "   <table class=\"table table-striped\"> \n"
                + "    <tbody> \n"
                + "     <tr> \n"
                + "      <th>from</th> \n"
                + "      <th>to</th> \n"
                + "     </tr> \n"
                + "     <tr> \n"
                + "      <td>http://from1</td> \n"
                + "      <td>http://to1</td> \n"
                + "     </tr> \n"
                + "     <tr> \n"
                + "      <td>http://from2</td> \n"
                + "      <td>http://to2</td> \n"
                + "     </tr> \n"
                + "     <tr> \n"
                + "      <td>http://from3</td> \n"
                + "      <td>http://to3</td> \n"
                + "     </tr> \n"
                + "    </tbody> \n"
                + "   </table> \n"
                + "  </div> \n"
                + " </div> \n"
                + "</div>");
    }
}
