package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import com.github.t1.metadeployer.tools.html.*;
import com.github.t1.metadeployer.tools.html.Table.TableRow;
import org.jsoup.nodes.Element;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.List;

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

    private class DeploymentsHtml extends Html {
        private final List<Deployment> deployments;
        private Table table;
        private Collection<Stage> mergedStages;
        private List<ClusterNode> mergedNodes;

        public DeploymentsHtml(List<Deployment> deployments) {
            this.deployments = deployments;

            this.mergedStages = mergedStages();
            this.mergedNodes = mergedStages.stream().flatMap(Stage::nodes).collect(toList());

            header();
            tableHeader();
            tableBody();
            footer();
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
        }

        @Override public void footer() {
            super.footer();
            script("/script.js");
        }

        private void tableHeader() {
            this.table = fullWidthContainer().table().id("deployables");
            stagesHeader();
            nodesHeader();
        }

        private void stagesHeader() {
            TableRow row = table.tr();
            row.th().attr("rowspan", "2").text("Cluster");
            row.th().attr("rowspan", "2").text("Application");
            mergedStages.forEach(stage -> row
                    .th()
                    .addClass("stage")
                    .attr("colspan", Integer.toString(stage.getCount()))
                    .text(stage.getName()));
        }

        private void nodesHeader() {
            TableRow row = table.tr();
            mergedNodes.forEach(node -> row.th().addClass("node").html(htmlFor(node)));
        }

        private String htmlFor(ClusterNode node) {
            String text = node.getStage().formattedIndex(node.getIndex());
            if (text.isEmpty())
                text = "&nbsp;";
            return text;
        }

        private void tableBody() {
            clusters.forEach(cluster -> {
                List<String> deployableNames = deployableNames(cluster);
                deployableNames.forEach(deployableName -> {
                    TableRow row = table.tr();
                    if (deployableName.equals(deployableNames.get(0)))
                        row.th()
                           .attr("rowspan", Integer.toString(deployableNames.size()))
                           .text(cluster.id());
                    row.th().addClass("deployable-name").text(deployableName);

                    mergedNodes.stream()
                               .map(node -> deployments
                                       .stream()
                                       .filter(deployment -> deployment.getName().equals(deployableName))
                                       .filter(deployment -> deployment.getNode().matchStageNameAndIndex(node))
                                       .filter(deployment -> Objects.equals(
                                               deployment.getNode().getCluster().getSlot().getName(),
                                               cluster.getSlot().getName()))
                                       .findAny()
                                       .map(this::cell)
                                       .orElse(new Element("div").addClass("no-deployment").html("-")))
                               .forEach(cell -> row.td().appendChild(cell));
                });
            });
        }

        private List<String> deployableNames(Cluster cluster) {
            List<String> deployableNames = deployments
                    .stream()
                    .filter(deployment -> on(cluster, deployment))
                    .map(Deployment::getName)
                    .distinct()
                    .collect(toList());
            if (deployableNames.isEmpty())
                deployableNames = singletonList("?");
            return deployableNames;
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
