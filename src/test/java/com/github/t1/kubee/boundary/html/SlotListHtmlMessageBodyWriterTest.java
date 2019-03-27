package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.model.Slot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

import static com.github.t1.kubee.model.ClusterTest.SLOT_0;
import static com.github.t1.kubee.model.ClusterTest.SLOT_1;
import static com.github.t1.kubee.tools.html.Html.BOOTSTRAP_CSS;
import static com.github.t1.kubee.tools.html.Html.ICONS_CSS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class SlotListHtmlMessageBodyWriterTest {
    private final SlotListHtmlMessageBodyWriter writer = new SlotListHtmlMessageBodyWriter();

    @Test
    public void shouldWriteHtml() throws Exception {
        List<Slot> slots = asList(SLOT_0, SLOT_1);
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(slots, null, null, null, null, null, out);

        Document html = Jsoup.parse(out.toString());
        assertThat(html.title()).isEqualTo("Slots");
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
            + "     <th>Slot</th> \n"
            + "     <th>http</th> \n"
            + "     <th>https</th> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>0</th> \n"
            + "     <td>8080</td> \n"
            + "     <td>8443</td> \n"
            + "    </tr> \n"
            + "    <tr> \n"
            + "     <th>1</th> \n"
            + "     <td>8180</td> \n"
            + "     <td>8543</td> \n"
            + "    </tr> \n"
            + "   </tbody> \n"
            + "  </table> \n"
            + " </div> \n"
            + "</div>");
    }
}
