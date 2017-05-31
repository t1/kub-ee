package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.*;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;

import static java.nio.charset.StandardCharsets.*;
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
        private Document html;
        private Element table;
        private List<Deployment> deployments;
        private Collection<Stage> mergedStages;
        private List<ClusterNode> mergedNodes;

        public void write(List<Deployment> deployments) throws IOException {
            this.deployments = deployments;
            this.mergedStages = mergedStages();
            this.mergedNodes = mergedStages.stream().flatMap(Stage::nodes).collect(toList());

            header();
            tableHeader();
            tableBody();

            out.append("<!DOCTYPE html>\n").write(html.outerHtml());
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
            html = Document.createShell("");
            html.title("Meta-Deployer");
            html.charset(UTF_8);
            html.head().appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", "http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css");
            html.head().appendElement("style")
                .appendChild(new DataNode("\n"
                        + "@CHARSET \"UTF-8\";\n"
                        + "\n"
                        + "body {\n"
                        + "    font-size: 16px;\n"
                        + "}\n"
                        + "\n"
                        + ".table > thead > tr > th,\n"
                        + ".table > tbody > tr > th,\n"
                        + ".table > tfoot > tr > th,\n"
                        + ".table > thead > tr > td,\n"
                        + ".table > tbody > tr > td,\n"
                        + ".table > tfoot > tr > td {\n"
                        + "    padding: 2px;\n"
                        + "    text-align: center;\n"
                        + "}\n"
                        + "\n"
                        + ".table th.cluster {\n"
                        + "    width: 20pt;\n"
                        + "    text-align: left;\n"
                        + "}\n"
                        + "\n"
                        + ".table th.app {\n"
                        + "    width: 20pt;\n"
                        + "    text-align: right;\n"
                        + "    white-space: nowrap;\n"
                        + "}\n"
                        + "\n"
                        + "th.stage, th.released {\n"
                        + "    font-weight: bold;\n"
                        + "    border-left: 1px solid gray;\n"
                        + "    border-right: 1px solid gray;\n"
                        + "}\n"
                        + "\n"
                        + ".service-table td a, .service-table th a {\n"
                        + "    color: black;\n"
                        + "}\n"
                        + "\n"
                        + ".service-table th.service a:hover {\n"
                        + "    color: #428bca;\n"
                        + "}\n"
                        + "\n"
                        + ".warning {\n"
                        + "    background-color: yellow !important;\n"
                        + "}\n"
                        + "\n"
                        + ".failure {\n"
                        + "    background-color: red !important;\n"
                        + "}\n"
                        + "\n"
                        + ".error {\n"
                        + "    background-color: black !important;\n"
                        + "}\n"
                        + "\n"
                        + ".error a {\n"
                        + "    color: white !important;\n"
                        + "}\n"
                        + "\n"
                        + ".timeout {\n"
                        + "    background-color: #f7cb1e !important;\n"
                        + "}\n"
                        + "\n"
                        + ".timeout.filtered a {\n"
                        + "    color: black !important;\n"
                        + "}\n"
                        + "\n"
                        + ".warning.filtered, .error.filtered, .failure.filtered, .timeout.filtered {\n"
                        + "    color: black !important;\n"
                        + "    background-color: silver !important;\n"
                        + "}\n"
                        + "\n"
                        + ".filtered .pie, .filtered .count {\n"
                        + "    display: none;\n"
                        + "}\n"
                        + "\n"
                        + ".released, .unreleased {\n"
                        + "    width: 3em;\n"
                        + "}\n"
                        + "\n"
                        + ".unreleased {\n"
                        + "    color: orange;\n"
                        + "}\n"
                        + "\n"
                        + ".table th.right {\n"
                        + "    text-align: right;\n"
                        + "    vertical-align: middle;\n"
                        + "}\n"
                        + "\n"
                        + ".table td.left, .table th.left {\n"
                        + "    text-align: left;\n"
                        + "}\n"
                        + "\n"
                        + ".table th.key {\n"
                        + "    text-align: right;\n"
                        + "    vertical-align: top;\n"
                        + "    border-right: 1px solid silver;\n"
                        + "}\n"
                        + "\n"
                        + ".table td.value {\n"
                        + "    text-align: left;\n"
                        + "    vertical-align: middle;\n"
                        + "    padding-left: 1em;\n"
                        + "}\n"
                        + "\n"
                        + ".timestamp {\n"
                        + "    font-size: small;\n"
                        + "    margin-top: -2em;\n"
                        + "}\n"
                        + "\n"
                        + "table.legend td, table.legend th {\n"
                        + "    text-align: center;\n"
                        + "    font-size: small;\n"
                        + "    padding: 5px;\n"
                        + "    border-top: 1px solid gray;\n"
                        + "    border-bottom: 1px solid gray;\n"
                        + "}\n"
                        + "\n"
                        + "p.legend {\n"
                        + "    font-size: small;\n"
                        + "    margin-top: 2em;\n"
                        + "}\n"
                        + "\n"
                        + ".legend td a {\n"
                        + "    color: black;\n"
                        + "}\n"
                        + "\n"
                        + "img.pie {\n"
                        + "    width: 18px;\n"
                        + "    border-radius: 100%;\n"
                        + "    border: 1px solid black;\n"
                        + "}\n"
                        + "\n"
                        + ".count {\n"
                        + "    font-size: small;\n"
                        + "    padding: 0.1em;\n"
                        + "}\n"
                        + "\n", ""));
            html.head().appendElement("script")
                .appendChild(new DataNode("\n"
                        + "function drag_start(ev) {\n"
                        // + "  console.log(\"drag start\");\n"
                        + "  ev.currentTarget.style.border = \"dashed\";\n"
                        + "  ev.dataTransfer.setData(\"text\", ev.target.id);\n"
                        + "}\n"
                        + "\n"
                        + "function drag_enter(ev) {\n"
                        + "  ev.preventDefault();\n"
                        // + "  console.log(\"drag enter\");\n"
                        + "  ev.currentTarget.style.background = \"lightblue\";\n"
                        + "}\n"
                        + "\n"
                        + "function drag_over(ev) {\n"
                        + "  ev.preventDefault();\n"
                        // + "  console.log(\"drag over\");\n"
                        + "}\n"
                        + "\n"
                        + "function drag_leave(ev) {\n"
                        + "  ev.preventDefault();\n"
                        // + "  console.log(\"drag leave\");\n"
                        + "  ev.currentTarget.style.background = null;\n"
                        + "}\n"
                        + "\n"
                        + "function drop_handler(ev) {\n"
                        + "  ev.preventDefault();\n"
                        + "  var id = ev.dataTransfer.getData(\"text\");\n"
                        + "  if (ev.dataTransfer.effectAllowed == \"copy\") {\n"
                        + "    console.log(\"copy \" + id + \"->\"+ ev.target.id);\n"
                        + "    var nodeCopy = document.getElementById(id).cloneNode(true);\n"
                        + "    nodeCopy.id = \"newId\";\n"
                        + "    ev.target.appendChild(nodeCopy);\n"
                        + "  } else {\n"
                        + "    console.log(\"move \" + id + \"->\"+ ev.target.id);\n"
                        + "    ev.target.appendChild(document.getElementById(id));\n"
                        + "  }\n"
                        + "}\n"
                        + "\n"
                        + "function drag_end(ev) {\n"
                        + "  ev.preventDefault();\n"
                        // + "  console.log(\"drag end\");\n"
                        + "  ev.target.style.border = null;\n"
                        + "  ev.dataTransfer.clearData();\n"
                        + "}\n", ""));
        }

        private void tableHeader() {
            table = html.body()
                        .appendElement("div").addClass("table-responsive")
                        .appendElement("table").addClass("table table-striped")
                        .appendElement("tbody");
            stagesHeader();
            nodesHeader();
        }

        private void stagesHeader() {
            Element row = table.appendElement("tr");
            row.appendElement("th").text("Cluster");
            row.appendElement("th").text("Application");
            mergedStages.forEach(stage -> row.appendElement("th")
                                             .addClass("stage")
                                             .attr("colspan", Integer.toString(stage.getCount()))
                                             .text(stage.getName()));
        }

        private void nodesHeader() {
            Element row = table.appendElement("tr");
            row.appendElement("th");
            row.appendElement("th");
            mergedNodes.forEach(node -> row.appendElement("th")
                                           .addClass("node")
                                           .text(node.getStage().formattedIndex(node.getIndex())));
        }

        private void tableBody() {
            clusters.forEach(cluster -> {
                List<String> deploymentNames = deploymentNames(cluster);
                deploymentNames.forEach(deploymentName -> {
                    Element row = table.appendElement("tr");
                    if (deploymentName.equals(deploymentNames.get(0)))
                        row.appendElement("th")
                           .attr("rowspan", Integer.toString(deploymentNames.size()))
                           .text(cluster.id());
                    row.appendElement("th").addClass("app").text(deploymentName);

                    mergedNodes.stream()
                               .map(n -> deployments
                                       .stream()
                                       .filter(deployment -> deployment.getName().equals(deploymentName))
                                       .filter(deployment -> deployment.getClusterNode().matchStageNameAndIndex(n))
                                       .findAny()
                                       .map(this::cell)
                                       .orElse(new Element("div").addClass("no-deployment").html("-")))
                               .forEach(cell -> row.appendElement("td").appendChild(cell));
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
                    .attr("ondrop", "drop_handler(event);")
                    .attr("ondragenter", "drag_enter(event);")
                    .attr("ondragover", "drag_over(event);")
                    .attr("ondragleave", "drag_leave(event);")
                    .text(deployment.hasError() ? deployment.getError() : deployment.getVersion());
        }

        private boolean on(Cluster cluster, Deployment deployment) {
            return deployment.getClusterNode().getCluster().getHost().equals(cluster.getHost())
                    && Objects.equals(deployment.getSlotName(), cluster.getSlotName());
        }
    }
}
