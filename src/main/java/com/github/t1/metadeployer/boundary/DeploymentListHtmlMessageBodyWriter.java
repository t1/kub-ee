package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.boundary.AbstractHtml.HtmlTable.HtmlTableRow;
import com.github.t1.metadeployer.model.*;
import org.jsoup.nodes.Element;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class DeploymentListHtmlMessageBodyWriter implements MessageBodyWriter<List<Deployment>> {
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
        out.write(new DeploymentsHtml(deployments).toString());
        out.flush();
    }

    private class DeploymentsHtml extends AbstractHtml {
        private final List<Deployment> deployments;
        private HtmlTable table;
        private Collection<Stage> mergedStages;
        private List<ClusterNode> mergedNodes;

        public DeploymentsHtml(List<Deployment> deployments) {
            this.deployments = deployments;

            this.mergedStages = mergedStages();
            this.mergedNodes = mergedStages.stream().flatMap(Stage::nodes).collect(toList());

            header();
            tableHeader();
            tableBody();
        }

        private Collection<Stage> mergedStages() {
            Map<String, Stage> map = new LinkedHashMap<>();
            clusters.stream().flatMap(Cluster::stages)
                    .map(this::copyNameCountLength)
                    .forEach(stage -> map.merge(stage.getName(), stage, Stage::largerCount));
            return map.values();
        }

        private Stage copyNameCountLength(Stage stage) {
            return Stage.builder()
                        .name(stage.getName())
                        .count(stage.getCount())
                        .indexLength(stage.getIndexLength())
                        .build();
        }

        private void header() {
            header("Meta-Deployer");
            script("/script.js");
        }

        private void tableHeader() {
            this.table = table();
            stagesHeader();
            nodesHeader();
        }

        private void stagesHeader() {
            HtmlTableRow row = table.tr();
            row.th().text("Cluster");
            row.th().text("Application");
            mergedStages.forEach(stage -> row.th()
                                             .addClass("stage")
                                             .attr("colspan", Integer.toString(stage.getCount()))
                                             .text(stage.getName()));
        }

        private void nodesHeader() {
            HtmlTableRow row = table.tr();
            row.th();
            row.th();
            mergedNodes.forEach(node -> row.th()
                                           .addClass("node")
                                           .text(node.getStage().formattedIndex(node.getIndex())));
        }

        private void tableBody() {
            clusters.forEach(cluster -> {
                List<String> deploymentNames = deploymentNames(cluster);
                deploymentNames.forEach(deploymentName -> {
                    HtmlTableRow row = table.tr();
                    if (deploymentName.equals(deploymentNames.get(0)))
                        row.th()
                           .attr("rowspan", Integer.toString(deploymentNames.size()))
                           .text(cluster.id());
                    row.th().addClass("app").text(deploymentName);

                    mergedNodes.stream()
                               .map(n -> deployments
                                       .stream()
                                       .filter(deployment -> deployment.getName().equals(deploymentName))
                                       .filter(deployment -> deployment.getNode().matchStageNameAndIndex(n))
                                       .findAny()
                                       .map(this::cell)
                                       .orElse(new Element("div").addClass("no-deployment").html("-")))
                               .forEach(cell -> row.td().appendChild(cell));
                });
            });
        }

        private List<String> deploymentNames(Cluster cluster) {
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

        private Element cell(Deployment deployment) {
            return new Element("div")
                    .addClass("deployment")
                    .attr("id", deployment.id())
                    .attr("draggable", "true")
                    .attr("ondragstart", "drag_start(event);")
                    .attr("ondragend", "drag_end(event);")
                    .attr("ondragenter", "drag_enter(event);")
                    .attr("ondragover", "drag_over(event);")
                    .attr("ondragleave", "drag_leave(event);")
                    .attr("ondrop", "drop_handler(event);")
                    .text(deployment.hasError() ? deployment.getError() : deployment.getVersion());
        }

        private boolean on(Cluster cluster, Deployment deployment) {
            return deployment.getNode().getCluster().getHost().equals(cluster.getHost())
                    && Objects.equals(deployment.getSlotName(), cluster.getSlot().getName());
        }
    }
}
