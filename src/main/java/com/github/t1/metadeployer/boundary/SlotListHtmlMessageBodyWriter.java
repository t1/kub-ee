package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Slot;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;

import static java.nio.charset.StandardCharsets.*;
import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class SlotListHtmlMessageBodyWriter implements MessageBodyWriter<List<Slot>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
                && ((ParameterizedType) genericType).getRawType().equals(List.class)
                && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Slot.class);
    }

    @Override
    public long getSize(List<Slot> slots, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Slot> slots, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        new SlotsWriter(out).write(slots);
        out.flush();
    }

    @RequiredArgsConstructor
    private class SlotsWriter {
        private final Writer out;
        private List<Slot> slots;
        private Document html = Document.createShell("");
        private Element table;

        public void write(List<Slot> slots) throws IOException {
            this.slots = slots;

            header();
            table = html.body()
                        .appendElement("div").addClass("table-responsive")
                        .appendElement("table").addClass("table table-striped")
                        .appendElement("tbody");
            tableHeader();
            this.slots.forEach(this::slotRow);

            out.append("<!DOCTYPE html>\n").write(html.outerHtml());
        }

        private void header() {
            html.title("Slots");
            html.charset(UTF_8);
            html.head().appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", "http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css");
        }

        private void tableHeader() {
            Element row = table.appendElement("tr");
            row.appendElement("th").text("Slot");
            row.appendElement("th").text("http");
            row.appendElement("th").text("https");
        }

        private void slotRow(Slot slot) {
            Element row = table.appendElement("tr");
            row.appendElement("th").text(slot.getName());
            row.appendElement("td").text(Integer.toString(slot.getHttp()));
            row.appendElement("td").text(Integer.toString(slot.getHttps()));
        }
    }
}
