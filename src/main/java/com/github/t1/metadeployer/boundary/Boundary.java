package com.github.t1.metadeployer.boundary;

import com.github.t1.log.Logged;
import com.github.t1.metadeployer.control.Controller;
import com.github.t1.metadeployer.model.*;
import lombok.Builder;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.log.LogLevel.*;
import static com.github.t1.metadeployer.model.ClusterNode.*;
import static java.util.Arrays.*;
import static java.util.Locale.*;
import static java.util.stream.Collectors.*;

@Logged(level = INFO)
@Slf4j
@Path("/")
@Stateless
public class Boundary {
    @Inject List<Cluster> clusters;
    @Context UriInfo uriInfo;

    @Inject Controller controller;

    @GET public List<Link> getLinks() {
        return asList(
                link("Load Balancers"),
                link("Reverse Proxies"),
                link("Clusters"),
                link("Slots"),
                link("Stages"),
                link("Deployments")
        );
    }

    private Link link(String title) {
        String method = "get" + title.replace(" ", "");
        UriBuilder href = uriInfo.getBaseUriBuilder().path(Boundary.class, method);
        String rel = title.toLowerCase(US).replaceAll(" ", "-");
        return Link.fromUriBuilder(href).rel(rel).title(title).build();
    }


    @Path("/load-balancers")
    @GET public List<LoadBalancer> getLoadBalancers() {
        return controller.readNginxConfig()
                         .getUpstreams()
                         .stream()
                         .map(server -> LoadBalancer
                                 .builder()
                                 .name(server.getName())
                                 .method(server.getMethod())
                                 .servers(server.getServers())
                                 .build())
                         .collect(toList());
    }

    @Path("/reverse-proxies")
    @GET public List<ReverseProxy> getReverseProxies() {
        return controller.readNginxConfig()
                         .getServers()
                         .stream()
                         .map(server -> ReverseProxy
                                 .builder()
                                 .from(URI.create("http://" + server.getName() + ":" + server.getListen()))
                                 .to(URI.create(server.getLocation().getPass()))
                                 .build())
                         .collect(toList());
    }


    @Path("/clusters")
    @GET public List<Cluster> getClusters() { return clusters; }

    @Path("/clusters/{name}")
    @GET public Cluster getCluster(@PathParam("name") String name) {
        return clusters.stream()
                       .filter(cluster -> cluster.getSimpleName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("cluster not found: '" + name + "'"));
    }


    @Path("/slots")
    @GET public List<Slot> getSlots() {
        return clusters.stream().map(Cluster::getSlot).sorted().distinct().collect(toList());
    }

    @Path("/slots/{name}")
    @GET public Slot getSlot(@PathParam("name") String name) {
        return clusters.stream()
                       .map(Cluster::getSlot)
                       .filter(slot -> slot.getName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("slot not found: '" + name + "'"));
    }


    @Path("/stages")
    @GET public List<Stage> getStages() {
        return clusters.stream().flatMap(Cluster::stages).sorted().distinct().collect(toList());
    }

    @Path("/stages/{name}")
    @GET public Stage getStage(@PathParam("name") String name) {
        return clusters.stream()
                       .flatMap(Cluster::stages)
                       .filter(stage -> stage.getName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("stage not found: '" + name + "'"));
    }


    @Path("/deployments")
    @GET public List<Deployment> getDeployments() {
        return clusters.stream().flatMap(this::fromCluster).collect(toList());
    }

    private Stream<Deployment> fromCluster(Cluster cluster) {
        return cluster.stages().flatMap(stage -> stage.nodes(cluster)).flatMap(controller::fetchDeploymentsOn);
    }

    @Path("/deployments/{id}")
    @GET public GetDeploymentResponse getDeployment(@PathParam("id") String id) {
        ClusterNode node = fromId(id, clusters);
        String deployableName = deployableName(id);
        Deployment deployment = controller.fetchDeploymentsOn(node)
                                          .filter(d -> d.getName().equals(deployableName))
                                          .findFirst()
                                          .orElseThrow(() -> new DeployableNotFoundException(deployableName, node));
        List<Version> available = controller.fetchVersions(node, deployment);
        return GetDeploymentResponse
                .builder()
                .id(id)
                .available(available)
                .build();
    }

    private static String deployableName(String id) { return id.split(":")[4]; }


    public static class DeployableNotFoundException extends BadRequestException {
        public DeployableNotFoundException(String deployableName, ClusterNode node) {
            super("deployable '" + deployableName + "' not found on '" + node.id() + "'");
        }
    }

    @Data
    @Builder
    public static class GetDeploymentResponse {
        private String id;
        private List<Version> available;
    }


    @Path("/deployments/{id}")
    @POST public Response postDeployments(@PathParam("id") String id, @FormParam("version") String version) {
        ClusterNode node = fromId(id, clusters);
        controller.startVersionDeploy(node.deployerUri(), deployableName(id), version);
        return Response.accepted().build();
    }
}
