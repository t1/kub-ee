package com.github.t1.kubee.boundary.html;


import com.github.t1.kubee.model.Stage;
import com.github.t1.kubee.tools.html.*;
import com.github.t1.kubee.tools.html.Table.TableRow;

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

    private class StagesHtml extends Html {
        private final List<Stage> stages;
        private Table table;

        private StagesHtml(List<Stage> stages) {
            this.stages = stages;

            header("Stages");
            container().fluid().with(table = new Table());
            tableHeader();
            this.stages.forEach(this::stageRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th("Stage");
            row.th("prefix");
            row.th("suffix");
            row.th("path");
            row.th("count");
            row.th("index-length");
        }

        private void stageRow(Stage stage) {
            TableRow row = table.tr();
            row.th(stage.getName());
            row.td(stage.getPrefix());
            row.td(stage.getSuffix());
            row.td(stage.getPath());
            row.td(stage.getCount());
            row.td(stage.getIndexLength());
        }
    }
}
