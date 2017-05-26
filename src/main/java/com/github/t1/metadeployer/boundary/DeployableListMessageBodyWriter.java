package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.*;
import lombok.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.*;

import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class DeployableListMessageBodyWriter implements MessageBodyWriter<List<Deployable>> {
    @Inject List<Cluster> clusters;

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return true;
    }

    @Override
    public long getSize(List<Deployable> deployables, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Deployable> deployables, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        new DeployablesWriter(out).write(deployables);
        out.flush();
    }

    @RequiredArgsConstructor
    private class DeployablesWriter {
        private final Writer out;

        public void write(List<Deployable> deployables) {
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
                    + "    <link rel='stylesheet' href=\"../style.css\" />\n"
                    + "</head>\n"
                    + "<body>\n");
        }

        private void body() {
            out("<div class=\"table-responsive\">\n"
                    + "<table class=\"table table-striped service-table\">\n"
                    + "<tr>\n"
                    + "    <th>Cluster</th>\n"
                    + "    <th>Application</th>\n");
            mergedStages().forEach(this::out);
            out("</tr>\n"
                    + "<tr>\n"
                    + "    <th></th>\n"
                    + "    <th></th>\n");
            clusters.stream().flatMap(Cluster::stages).forEach(stage ->
                    IntStream.range(1, stage.getCount() + 1).forEach(index ->
                            out("    <th class=\"node\">" + stage.formattedIndex(index) + "</th>\n")));
            out("</tr>\n");

            // CLUSTERS.forEach(cluster -> {
            //     List<String> deployableNames = deployableNames(deployers, cluster);
            //     deployableNames.forEach(deployableName -> {
            //         out("<tr>\n");
            //         if (deployableName.equals(deployableNames.get(0)))
            //             out("  <th class='cluster' rowspan='" + deployableNames.size() + "'>" + cluster + "</th>\n");
            //         out("  <th class='service'>");
            //         out(deployableName);
            //         out("</th>\n");
            //
            //         cluster.allNodes()
            //                .forEach(node -> deployers
            //                        .stream()
            //                        .filter(instance -> instance.isOn(node))
            //                        .forEach(instance -> {
            //                            out("  <td>");
            //                            out(instance.hasError() ? instance.getError()
            //                                    : instance.deployable(deployableName)
            //                                              .map(Deployable::getVersion)
            //                                              .orElse("-"));
            //                            out("</td>\n");
            //                        }));
            //
            //         out("</tr>\n");
            //     });
            // });

            out("</table>\n"
                    + "</div>\n");
        }

        private void footer() {
            out("</body>\n"
                    + "</html>\n");
        }

        private Stream<Stage> mergedStages() {
            Map<String, Stage> map = new LinkedHashMap<>();
            clusters.stream().flatMap(Cluster::stages).forEach(stage -> map.merge(stage.getName(), stage,
                    (l, r) -> (l.getCount() > r.getCount()) ? l : r));
            return map.values().stream();
        }

        private void out(Stage stage) {
            out("    <th colspan=\"" + stage.getCount() + "\" class=\"stage\">" + stage.getName() + "</th>\n");
        }


        // private List<String> deployableNames(List<DeployerInstance> deployers, Cluster cluster) {
        //     List<String> deployableNames = deployers.stream()
        //                                             .filter(instance -> instance.isOn(cluster))
        //                                             .flatMap(DeployerInstance::deployableNames)
        //                                             .distinct()
        //                                             .collect(toList());
        //     if (deployableNames.isEmpty())
        //         deployableNames = singletonList("?");
        //     return deployableNames;
        // }

        @SneakyThrows(IOException.class) private void out(String str) { out.write(str); }
    }
}
