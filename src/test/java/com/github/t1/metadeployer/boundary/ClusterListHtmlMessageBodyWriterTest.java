package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.*;
import java.util.List;

import static com.github.t1.metadeployer.tools.html.Html.*;
import static java.nio.charset.StandardCharsets.*;
import static org.assertj.core.api.Assertions.*;

public class ClusterListHtmlMessageBodyWriterTest {
    private ClusterListHtmlMessageBodyWriter writer = new ClusterListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<Cluster> clusters = ClusterTest.readClusterConfig().clusters();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(clusters, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Meta-Deployer");
        assertThat(html.charset()).isEqualTo(UTF_8);
        assertThat(html.getElementsByAttributeValue("rel", "stylesheet"))
                .extracting(element -> element.attr("href"))
                .containsOnly(BOOTSTRAP_BASE + "/css/bootstrap.min.css", "../style.css");
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
                + "     <td> prefix: '' <br> suffix: 'dev' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> indexLength: 2 </td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>server-a:2</th> \n"
                + "     <td> prefix: '' <br> suffix: 'dev' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> indexLength: 2 </td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>server-b:2</th> \n"
                + "     <td> prefix: '' <br> suffix: 'test' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: 'qa' <br> suffix: '' <br> count: 2 <br> indexLength: 2 </td> \n"
                + "     <td> prefix: '' <br> suffix: '' <br> count: 3 <br> indexLength: 2 </td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>localhost:1</th> \n"
                + "     <td> - </td> \n"
                + "     <td> - </td> \n"
                + "     <td> prefix: '' <br> suffix: '' <br> count: 1 <br> indexLength: 0 </td> \n"
                + "    </tr> \n"
                + "   </tbody> \n"
                + "  </table> \n"
                + " </div> \n"
                + "</div>");
    }
}
