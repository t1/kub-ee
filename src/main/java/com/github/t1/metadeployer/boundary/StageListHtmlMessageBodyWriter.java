package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Stage;
import org.jsoup.nodes.Element;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;

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
        out.write(new StagesHtml(stages).toString());
        out.flush();
    }

    private class StagesHtml extends AbstractHtml {
        private final List<Stage> stages;
        private Element table;

        private StagesHtml(List<Stage> stages) {
            this.stages = stages;

            header("Stages");
            table = table();
            tableHeader();
            this.stages.forEach(this::stageRow);
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
