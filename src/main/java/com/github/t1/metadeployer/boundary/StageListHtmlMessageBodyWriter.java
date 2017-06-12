package com.github.t1.metadeployer.boundary;


import com.github.t1.metadeployer.model.Stage;
import com.github.t1.metadeployer.tools.html.*;
import com.github.t1.metadeployer.tools.html.Table.TableRow;

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
            table = withoutContainer().table();
            tableHeader();
            this.stages.forEach(this::stageRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th().text("Stage");
            row.th().text("prefix");
            row.th().text("suffix");
            row.th().text("path");
            row.th().text("count");
            row.th().text("indexLength");
        }

        private void stageRow(Stage stage) {
            TableRow row = table.tr();
            row.th().text(stage.getName());
            row.td().text(stage.getPrefix());
            row.td().text(stage.getSuffix());
            row.td().text(stage.getPath());
            row.td().text(Integer.toString(stage.getCount()));
            row.td().text(Integer.toString(stage.getIndexLength()));
        }
    }
}
