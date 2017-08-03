package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.model.LoadBalancer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.*;
import java.util.List;

import static com.github.t1.kubee.tools.html.Html.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class LoadBalancerListHtmlMessageBodyWriterTest {
    private final LoadBalancerListHtmlMessageBodyWriter writer = new LoadBalancerListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<LoadBalancer> loadBalancers = asList(
                LoadBalancer.builder().name("backend1").method("least_conn").server("b1s1").server("b1s2").build(),
                LoadBalancer.builder().name("backend2").method("least_conn").server("b2s1").build()
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
                + "      <th>Name</th> \n"
                + "      <th>Method</th> \n"
                + "      <th>Servers</th> \n"
                + "     </tr> \n"
                + "     <tr> \n"
                + "      <td>backend1</td> \n"
                + "      <td>least_conn</td> \n"
                + "      <td>b1s1<br> b1s2</td> \n"
                + "     </tr> \n"
                + "     <tr> \n"
                + "      <td>backend2</td> \n"
                + "      <td>least_conn</td> \n"
                + "      <td>b2s1</td> \n"
                + "     </tr> \n"
                + "    </tbody> \n"
                + "   </table> \n"
                + "  </div> \n"
                + " </div> \n"
                + "</div>");
    }
}