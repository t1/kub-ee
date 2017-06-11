package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
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
public class StageListHtmlMessageBodyWriter implements MessageBodyWriter<List<Stage>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
                && ((ParameterizedType) genericType).getRawType().equals(List.class)
                && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Stage.class);
    }

    @Override
    public long getSize(List<Stage> stages, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Stage> stages, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        new StagesWriter(out).write(stages);
        out.flush();
    }

    @RequiredArgsConstructor
    private class StagesWriter {
        private final Writer out;
        private List<Stage> stages;
        private Document html = Document.createShell("");
        private Element table;

        public void write(List<Stage> stages) throws IOException {
            this.stages = stages;

            header();
            table = html.body()
                        .appendElement("div").addClass("table-responsive")
                        .appendElement("table").addClass("table table-striped")
                        .appendElement("tbody");
            tableHeader();
            this.stages.forEach(this::stageRow);

            out.append("<!DOCTYPE html>\n").write(html.outerHtml());
        }

        private void header() {
            html.title("Stages");
            html.charset(UTF_8);
            html.head().appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", "http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css");
        }

        private void tableHeader() {
            Element row = table.appendElement("tr");
            row.appendElement("th").text("Stage");
            row.appendElement("th").text("prefix");
            row.appendElement("th").text("suffix");
            row.appendElement("th").text("path");
            row.appendElement("th").text("count");
            row.appendElement("th").text("indexLength");
        }

        private void stageRow(Stage stage) {
            Element row = table.appendElement("tr");
            row.appendElement("th").text(stage.getName());
            row.appendElement("td").text(stage.getPrefix());
            row.appendElement("td").text(stage.getSuffix());
            row.appendElement("td").text(stage.getPath());
            row.appendElement("td").text(Integer.toString(stage.getCount()));
            row.appendElement("td").text(Integer.toString(stage.getIndexLength()));
        }
    }
}
