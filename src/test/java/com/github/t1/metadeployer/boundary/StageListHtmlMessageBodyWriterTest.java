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

public class StageListHtmlMessageBodyWriterTest {
    private final StageListHtmlMessageBodyWriter writer = new StageListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        Boundary boundary = new Boundary();
        boundary.clusters = ClusterTest.readClusterConfig().clusters();
        List<Stage> stages = boundary.getStages();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(stages, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Stages");
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
                + "     <th>Stage</th> \n"
                + "     <th>prefix</th> \n"
                + "     <th>suffix</th> \n"
                + "     <th>path</th> \n"
                + "     <th>count</th> \n"
                + "     <th>indexLength</th> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>DEV</th> \n"
                + "     <td></td> \n"
                + "     <td>dev</td> \n"
                + "     <td>deployer</td> \n"
                + "     <td>2</td> \n"
                + "     <td>2</td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>DEV</th> \n"
                + "     <td></td> \n"
                + "     <td>test</td> \n"
                + "     <td>deployer</td> \n"
                + "     <td>2</td> \n"
                + "     <td>2</td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>PROD</th> \n"
                + "     <td></td> \n"
                + "     <td></td> \n"
                + "     <td>deployer</td> \n"
                + "     <td>1</td> \n"
                + "     <td>0</td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>PROD</th> \n"
                + "     <td></td> \n"
                + "     <td></td> \n"
                + "     <td>deployer</td> \n"
                + "     <td>3</td> \n"
                + "     <td>2</td> \n"
                + "    </tr> \n"
                + "    <tr> \n"
                + "     <th>QA</th> \n"
                + "     <td>qa</td> \n"
                + "     <td></td> \n"
                + "     <td>deployer</td> \n"
                + "     <td>2</td> \n"
                + "     <td>2</td> \n"
                + "    </tr> \n"
                + "   </tbody> \n"
                + "  </table> \n"
                + " </div> \n"
                + "</div>");
    }
}
