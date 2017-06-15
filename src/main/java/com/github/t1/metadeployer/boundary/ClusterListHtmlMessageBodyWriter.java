package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import com.github.t1.metadeployer.tools.html.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.metadeployer.tools.html.Table.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;

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

            header("Meta-Deployer");
            this.table = fullWidthContainer().table();
            tableHeader();
            this.clusters.forEach(this::clusterRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th().text("Cluster");
            stageNames.forEach(text -> row.th().text(text));
        }

        private void clusterRow(Cluster cluster) {
            TableRow row = table.tr();
            row.th().text(cluster.id());
            stage(cluster).forEach(text -> row.td().append(text));
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
                    + " indexLength: " + stage.getIndexLength() + " ";
        }
    }
}
