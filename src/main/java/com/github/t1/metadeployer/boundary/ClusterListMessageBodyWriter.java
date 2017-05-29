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
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class ClusterListMessageBodyWriter implements MessageBodyWriter<List<Cluster>> {
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
        new ClustersWriter(out).write(clusters);
        out.flush();
    }

    @RequiredArgsConstructor
    private class ClustersWriter {
        private final Writer out;
        private List<Cluster> clusters;
        private List<String> stageNames;
        private Document html = Document.createShell("");
        private Element table;

        public void write(List<Cluster> clusters) throws IOException {
            this.clusters = clusters;
            this.stageNames = clusters
                    .stream()
                    .flatMap(Cluster::stages)
                    .map(Stage::getName)
                    .distinct()
                    .collect(toList());

            header();
            table = html.body()
                        .appendElement("div").addClass("table-responsive")
                        .appendElement("table").addClass("table table-striped")
                        .appendElement("tbody");
            tableHeader();
            this.clusters.forEach(this::clusterRow);

            out.append("<!DOCTYPE html>\n").write(html.outerHtml());
        }

        private void header() {
            html.title("Meta-Deployer");
            html.charset(UTF_8);
            html.head().appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", "http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css");
        }

        private void tableHeader() {
            Element row = table.appendElement("tr");
            row.appendElement("th").text("Cluster");
            stageNames.forEach(s -> row.appendElement("th").text(s));
        }

        private void clusterRow(Cluster cluster) {
            Element row = table.appendElement("tr");
            row.appendElement("th").text(cluster.getHost() + ":" + cluster.getPort());
            stage(cluster).forEach(s -> row.appendElement("td").append(s));
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
