package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterTest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

import static com.github.t1.kubee.tools.html.Html.BOOTSTRAP_CSS;
import static com.github.t1.kubee.tools.html.Html.ICONS_CSS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterListHtmlMessageBodyWriterTest {
    private ClusterListHtmlMessageBodyWriter writer = new ClusterListHtmlMessageBodyWriter();

    @Test void shouldWriteHtml() throws Exception {
        List<Cluster> clusters = ClusterTest.readClusterConfig();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(clusters, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Kub-EE");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
            .extracting(element -> element.attr("href"))
            .containsOnly(BOOTSTRAP_CSS, ICONS_CSS, "../style.css");
        assertThat(html.body().html()).isEqualTo(""
            + "<div class=\"container-fluid\"> \n"
            + " <div class=\"table-responsive\"> \n"
            + "  <table class=\"table table-striped\"> \n"
            + "   <tbody> \n"
            + "    <tr> \n"
            + "     <th>Cluster</th> \n"
            + "     <th>DEV</th> \n"
            + "     <th>QA</th> \n"
            + "     <th>PROD</th> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>server-a:1</th> \n"
            + "     <td> prefix: '' <br> suffix: 'dev' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> index-length: 2 </td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>server-a:2</th> \n"
            + "     <td> prefix: '' <br> suffix: 'dev' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> index-length: 2 </td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>server-b:2</th> \n"
            + "     <td> prefix: '' <br> suffix: 'test' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> index-length: 2 </td> \n"
            + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> index-length: 2 </td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>localhost:1</th> \n"
            + "     <td> - </td> \n"
            + "     <td> - </td> \n"
            + "     <td> prefix: '' <br> suffix: '' <br> count: 1 <br> index-length: 0 </td> \n"
            + "    </tr> \n"
            + "   </tbody> \n"
            + "  </table> \n"
            + " </div> \n"
            + "</div>");
    }
}
