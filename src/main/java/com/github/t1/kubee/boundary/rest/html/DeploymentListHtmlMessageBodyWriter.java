package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.control.Controller;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.html.CustomComponent;
import com.github.t1.kubee.tools.html.Html;
import com.github.t1.kubee.tools.html.Table;
import com.github.t1.kubee.tools.html.Table.TableRow;

import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.github.t1.kubee.entity.DeploymentStatus.running;
import static com.github.t1.kubee.tools.html.CustomComponent.div;
import static com.github.t1.kubee.tools.html.CustomComponent.span;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.MediaType.TEXT_HTML;

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
            script("react/umd/react.development.js");
            script("react-dom/umd/react-dom.development.js");
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
            row.th("Cluster").attr("rowspan", "2");
            row.th("Application").attr("rowspan", "2");
            mergedStages.forEach(stage -> row
                .th(stage.getName())
                .className("stage")
                .attr("colspan", stage.getCount()));
        }

        private void nodesHeader() {
            TableRow row = table.tr();
            mergedNodes.forEach(node -> row
                .th(htmlFor(node))
                .className("node")
                .attr("id", "node:" + node.getStage().getName() + ":" + node.getIndex()));
        }

        private String htmlFor(ClusterNode node) {
            return node.getStage().formattedIndex(node.getIndex());
        }

        private void tableBody() {
            controller.clusters().forEach(cluster -> {
                List<String> deployableNames = deployableNames(cluster);
                deployableNames.forEach(deployableName -> {
                    TableRow row = table.tr();
                    if (deployableName.equals(deployableNames.get(0)))
                        row.th(cluster.id())
                            .attr("id", "cluster:" + cluster.id())
                            .attr("rowspan", deployableNames.size());
                    row.th(deployableName).className("deployable-name");

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
                        .forEach(cell -> row.td(cell)
                            .attr("ondragover", "drag_over(event);")
                            .attr("ondragleave", "drag_leave(event);")
                            .attr("ondrop", "drop_handler(event);"));
                });
            });
        }

        private CustomComponent notDeployed(Cluster cluster, ClusterNode node, String deployableName) {
            return div()
                .className(DEPLOYMENT)
                .className("not-deployed")
                .id(cluster.id() + ":" + node.getStage().getName() + ":" + node.getIndex() + ":" + deployableName)
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

        private CustomComponent cell(Deployment deployment) {
            return div()
                .className(DEPLOYMENT, deployment.getStatus().name())
                .id(deployment.id())
                .attr("title", deployment.gav())
                .attr("draggable", "true")
                .attr("ondragstart", "drag_start(event);")
                .attr("ondragend", "drag_end(event);")
                .attr("onclick", "click_handler(event);")
                .with(span().className(icon(deployment)))
                .with(span()
                    .className("dropdown")
                    .with(span()
                        .className("dropdown-toggle")
                        .attr("data-toggle", "dropdown")
                        .with(span()
                            .className("version-name")
                            .text(deployment.hasError()
                                ? deployment.getError()
                                : deployment.getVersion()))
                        .with(span().className("caret")))
                    .with(div().className("dropdown-menu versions-menu")));
        }

        private String[] icon(Deployment deployment) {
            return (deployment.getStatus() == running) ? new String[0] : new String[]{"icon", "ion-md-eye-off"};
        }

        private boolean on(Cluster cluster, Deployment deployment) {
            return deployment.getNode().getCluster().getHost().equals(cluster.getHost())
                && Objects.equals(deployment.getSlotName(), cluster.getSlot().getName());
        }
    }
}
