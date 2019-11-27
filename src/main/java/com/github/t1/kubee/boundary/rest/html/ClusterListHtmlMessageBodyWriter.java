package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.html.Html;
import com.github.t1.kubee.tools.html.Table;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.html.Table.TableRow;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

@Provider
@Produces(TEXT_HTML)
public class ClusterListHtmlMessageBodyWriter implements MessageBodyWriter<List<Cluster>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
            && ((ParameterizedType) genericType).getRawType().equals(List.class)
            && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Cluster.class);
    }

    @Override
    public long getSize(List<Cluster> clusters, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Cluster> clusters, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(new ClustersHtml(clusters).toString());
        out.flush();
    }

    private class ClustersHtml extends Html {
        private final List<Cluster> clusters;
        private List<String> stageNames;
        private Table table;

        private ClustersHtml(List<Cluster> clusters) {
            this.clusters = clusters;

            this.stageNames = clusters
                .stream()
                .flatMap(Cluster::stages)
                .map(Stage::getName)
                .distinct()
                .collect(toList());

            header("Kub-EE");
            container().fluid().with(table = new Table());
            tableHeader();
            this.clusters.forEach(this::clusterRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th("Cluster");
            stageNames.forEach(row::th);
        }

        private void clusterRow(Cluster cluster) {
            TableRow row = table.tr();
            row.th(cluster.id());
            stage(cluster).forEach(row::td);
        }

        private Stream<String> stage(Cluster cluster) {
            return stageNames.stream().map(stageName ->
                cluster.stages()
                    .filter(stage -> stage.getName().equals(stageName))
                    .findAny()
                    .map(this::cell)
                    .orElse(" - "));
        }

        private String cell(Stage stage) {
            return ""
                + " prefix: '" + stage.getPrefix() + "' <br>"
                + " suffix: '" + stage.getSuffix() + "' <br>"
                + " count: " + stage.getCount() + " <br>"
                + " index-length: " + stage.getIndexLength() + " ";
        }
    }
}
