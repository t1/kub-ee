package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.boundary.rest.Boundary;
import com.github.t1.kubee.boundary.rest.BoundaryMockFactory;
import com.github.t1.kubee.model.Stage;
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

class StageListHtmlMessageBodyWriterTest {
    private final StageListHtmlMessageBodyWriter writer = new StageListHtmlMessageBodyWriter();

    @Test void shouldWriteHtml() throws Exception {
        Boundary boundary = BoundaryMockFactory.createWithClusters();
        List<Stage> stages = boundary.getStages();
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(stages, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Stages");
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
            + "     <th>Stage</th> \n"
            + "     <th>prefix</th> \n"
            + "     <th>suffix</th> \n"
            + "     <th>path</th> \n"
            + "     <th>count</th> \n"
            + "     <th>index-length</th> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>DEV</th> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>dev</td> \n"
            + "     <td>deployer</td> \n"
            + "     <td>2</td> \n"
            + "     <td>2</td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>DEV</th> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>test</td> \n"
            + "     <td>deployer</td> \n"
            + "     <td>2</td> \n"
            + "     <td>2</td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>PROD</th> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>deployer</td> \n"
            + "     <td>1</td> \n"
            + "     <td>0</td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>PROD</th> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>&nbsp;</td> \n"
            + "     <td>deployer</td> \n"
            + "     <td>3</td> \n"
            + "     <td>2</td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>QA</th> \n"
            + "     <td>qa</td> \n"
            + "     <td>&nbsp;</td> \n"
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
