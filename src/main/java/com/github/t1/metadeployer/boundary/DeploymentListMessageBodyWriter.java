package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import lombok.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class DeploymentListMessageBodyWriter implements MessageBodyWriter<List<Deployment>> {
    @Inject List<Cluster> clusters;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
                && ((ParameterizedType) genericType).getRawType().equals(List.class)
                && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Deployment.class);
    }

    @Override
    public long getSize(List<Deployment> deployments, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Deployment> deployments, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        new DeploymentsWriter(out).write(deployments);
        out.flush();
    }

    @RequiredArgsConstructor
    private class DeploymentsWriter {
        private final Writer out;

        @SneakyThrows(IOException.class) private void out(String str) { out.write(str); }

        public void write(List<Deployment> deployments) {
            header();
            body(deployments);
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

        private void body(List<Deployment> deployments) {
            out(""
                    + "<div class=\"table-responsive\">\n"
                    + "    <table class=\"table table-striped service-table\">\n"
                    + "        <tr>\n"
                    + "            <th>Cluster</th>\n"
                    + "            <th>Application</th>\n");
            mergedStages().forEach(this::out);
            out(""
                    + "        </tr>\n"
                    + "        <tr>\n"
                    + "            <th></th>\n"
                    + "            <th></th>\n");
            clusters.stream().flatMap(Cluster::stages).forEach(
                    stage -> stage.indexes().forEach(
                            index -> out("            <th class=\"node\">" + stage.formattedIndex(index) + "</th>\n")));
            out("        </tr>\n");

            clusters.forEach(cluster -> {
                List<String> deploymentNames = deploymentNames(deployments, cluster);
                deploymentNames.forEach(deploymentName -> {
                    out("        <tr>\n");
                    if (deploymentName.equals(deploymentNames.get(0)))
                        out("            <th class='cluster' rowspan='" + deploymentNames.size() + "'>"
                                + cluster.getHost()
                                + "</th>\n");
                    out("            <th class='service'>");
                    out(deploymentName);
                    out("</th>\n");

                    clusters.stream()
                            .flatMap(Cluster::stages)
                            .flatMap(stage -> stage.nodes(cluster))
                            .map(node -> deployments
                                    .stream()
                                    .filter(deployment -> deployment.isOn(node))
                                    .findAny()
                                    .map(this::cell)
                                    .orElse("-"))
                            .forEach(cell -> out("            <td>" + cell + "</td>\n"));

                    out("        </tr>\n");
                });
            });

            out("    </table>\n"
                    + "</div>\n");
        }

        private Stream<Stage> mergedStages() {
            Map<String, Stage> map = new LinkedHashMap<>();
            clusters.stream().flatMap(Cluster::stages).forEach(stage -> map.merge(stage.getName(),
                    Stage.builder().name(stage.getName()).count(stage.getCount()).build(),
                    (l, r) -> (l.getCount() > r.getCount()) ? l : r));
            return map.values().stream();
        }

        private List<String> deploymentNames(List<Deployment> deployments, Cluster cluster) {
            List<String> deploymentNames = deployments
                    .stream()
                    .filter(deployment -> on(cluster, deployment))
                    .map(Deployment::getName)
                    .distinct()
                    .collect(toList());
            if (deploymentNames.isEmpty())
                deploymentNames = singletonList("?");
            return deploymentNames;
        }

        private void out(Stage stage) {
            out("            <th colspan=\"" + stage.getCount() + "\" class=\"stage\">" + stage.getName() + "</th>\n");
        }

        private String cell(Deployment deployment) {
            return deployment.hasError() ? deployment.getError() : deployment.getVersion();
        }

        private boolean on(Cluster cluster, Deployment deployment) {
            return deployment.getCluster().getHost().equals(cluster.getHost());
        }
    }
}
