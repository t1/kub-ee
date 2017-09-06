package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.control.Controller;
import com.github.t1.kubee.model.*;
import com.github.t1.kubee.tools.html.*;
import com.github.t1.kubee.tools.html.Table.TableRow;
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
    @Inject Controller controller;
    @Context UriInfo uriInfo;

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
        private static final String DEPLOYMENT = "deployment";

        private final List<Deployment> deployments;
        private Table table;
        private Collection<Stage> mergedStages;
        private List<ClusterNode> mergedNodes;

        private DeploymentsHtml(List<Deployment> deployments) {
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
            controller.clusters().flatMap(Cluster::stages)
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
            header("Kub-EE");
        }

        @Override public void footer() {
            super.footer();
            script("react/react.js");
            script("react/react-dom.js");
            script("babel-standalone/babel.js");
            inlineScript("const baseUri = '" + uriInfo.getBaseUri() + "';");
            script("../script.js", "text/babel");
        }

        private void tableHeader() {
            container().fluid().with(table = new Table().id("deployables"));
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
            mergedNodes.forEach(node -> row
                    .th()
                    .addClass("node")
                    .attr("id", "node:" + node.getStage().getName() + ":" + node.getIndex())
                    .html(htmlFor(node)));
        }

        private String htmlFor(ClusterNode node) {
            String text = node.getStage().formattedIndex(node.getIndex());
            if (text.isEmpty())
                text = "&nbsp;";
            return text;
        }

        private void tableBody() {
            controller.clusters().forEach(cluster -> {
                List<String> deployableNames = deployableNames(cluster);
                deployableNames.forEach(deployableName -> {
                    TableRow row = table.tr();
                    if (deployableName.equals(deployableNames.get(0)))
                        row.th()
                           .attr("id", "cluster:" + cluster.id())
                           .attr("rowspan", Integer.toString(deployableNames.size()))
                           .text(cluster.id());
                    row.th().addClass("deployable-name").text(deployableName);

                    mergedNodes.stream()
                               .map(node -> deployments
                                       .stream()
                                       .filter(deployment -> deployment.getName().equals(deployableName))
                                       .filter(deployment -> deployment.getNode().getCluster().getHost()
                                                                       .equals(cluster.getHost()))
                                       .filter(deployment -> deployment.getNode().matchStageNameAndIndex(node))
                                       .filter(deployment -> Objects.equals(
                                               deployment.getNode().getCluster().getSlot().getName(),
                                               cluster.getSlot().getName()))
                                       .findAny()
                                       .map(this::cell)
                                       .orElse(notDeployed(cluster, node, deployableName)))
                               .forEach(cell -> row.td()
                                                   .attr("ondragover", "drag_over(event);")
                                                   .attr("ondragleave", "drag_leave(event);")
                                                   .attr("ondrop", "drop_handler(event);")
                                                   .appendChild(cell));
                });
            });
        }

        private Element notDeployed(Cluster cluster, ClusterNode node, String deployableName) {
            String id = cluster.id() + ":" + node.getStage().getName() + ":" + node.getIndex() + ":" + deployableName;
            return new Element("div")
                    .addClass(DEPLOYMENT)
                    .addClass("not-deployed")
                    .attr("id", id)
                    .html("-");
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
            Element cell = new Element("div")
                    .addClass(DEPLOYMENT)
                    .attr("id", deployment.id())
                    .attr("title", deployment.gav())
                    .attr("draggable", "true")
                    .attr("ondragstart", "drag_start(event);")
                    .attr("ondragend", "drag_end(event);")
                    .attr("onclick", "click_handler(event);");
            Element dropdown = cell.appendElement("span").addClass("dropdown");
            Element toggle = dropdown.appendElement("span")
                                     .addClass("dropdown-toggle")
                                     .attr("data-toggle", "dropdown");
            toggle.appendElement("span")
                  .addClass("version-name")
                  .text(deployment.hasError() ? deployment.getError() : deployment.getVersion());
            toggle.appendElement("span")
                  .addClass("caret");
            dropdown.appendElement("div").addClass("dropdown-menu versions-menu");
            return cell;
        }

        private boolean on(Cluster cluster, Deployment deployment) {
            return deployment.getNode().getCluster().getHost().equals(cluster.getHost())
                    && Objects.equals(deployment.getSlotName(), cluster.getSlot().getName());
        }
    }
}
