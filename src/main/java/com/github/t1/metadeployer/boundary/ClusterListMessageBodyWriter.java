package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import lombok.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;
import java.util.stream.Stream;

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

        @SneakyThrows(IOException.class) private void out(String str) { out.write(str); }

        public void write(List<Cluster> clusters) {
            this.clusters = clusters;
            this.stageNames = clusters.stream()
                                      .flatMap(Cluster::stages)
                                      .map(Stage::getName)
                                      .distinct()
                                      .collect(toList());

            header();
            body();
            footer();
        }

        private void header() {
            out("<!DOCTYPE html>\n"
                    + "<html>\n"
                    + "<head>\n"
                    + "    <title>Meta-Deployer</title>\n"
                    + "    <meta charset=\"utf-8\" />\n"
                    + "    <link rel='stylesheet' href=\"http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css\" />\n"
                    + "</head>\n"
                    + "<body>\n");
        }

        private void footer() {
            out("</body>\n"
                    + "</html>\n");
        }

        private void body() {
            out(""
                    + "<div class=\"table-responsive\">\n"
                    + "    <table class=\"table table-striped cluster-table\">\n"
                    + "        <tr>\n"
                    + "            <th>Cluster</th>\n");
            stageNames.forEach(s -> out("            <th class=\"stage\">" + s + "</th>\n"));
            out("        </tr>\n");

            clusters.forEach(this::out);

            out("    </table>\n"
                    + "</div>\n");
        }

        private void out(Cluster cluster) {
            out(""
                    + "            <tr>\n"
                    + "                <th>" + cluster.getHost() + ":" + cluster.getPort() + "</th>\n"
                    + "                <td>\n"
                    + stage(cluster).collect(joining("                </td>\n                <td>\n"))
                    + "                </td>\n"
                    + "            </tr>\n");
        }

        private Stream<String> stage(Cluster cluster) {
            return stageNames.stream().map(stageName ->
                    cluster.stages()
                           .filter(stage -> stage.getName().equals(stageName))
                           .findAny()
                           .map(this::cell)
                           .orElse("                    -\n"));
        }

        private String cell(Stage stage) {
            return ""
                    + "                    prefix: '" + stage.getPrefix() + "'<br>\n"
                    + "                    suffix: '" + stage.getSuffix() + "'<br>\n"
                    + "                    count: " + stage.getCount() + "<br>\n"
                    + "                    indexLength: " + stage.getIndexLength() + "\n";
        }
    }
}
