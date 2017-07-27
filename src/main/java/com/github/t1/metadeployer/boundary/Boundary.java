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
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.log.LogLevel.*;
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


    @GET @Path("/load-balancers") public List<LoadBalancer> getLoadBalancers() { return controller.getLoadBalancers(); }

    @GET @Path("/reverse-proxies")
    public List<ReverseProxy> getReverseProxies() { return controller.getReverseProxies(); }


    @GET @Path("/clusters") public List<Cluster> getClusters() { return clusters; }

    @GET @Path("/clusters/{name}") public Cluster getCluster(@PathParam("name") String name) {
        return clusters.stream()
                       .filter(cluster -> cluster.getSimpleName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("cluster not found: '" + name + "'"));
    }


    @GET @Path("/slots") public List<Slot> getSlots() {
        return clusters.stream().map(Cluster::getSlot).sorted().distinct().collect(toList());
    }

    @GET @Path("/slots/{name}") public Slot getSlot(@PathParam("name") String name) {
        return clusters.stream()
                       .map(Cluster::getSlot)
                       .filter(slot -> slot.getName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("slot not found: '" + name + "'"));
    }


    @GET @Path("/stages") public List<Stage> getStages() {
        return clusters.stream().flatMap(Cluster::stages).sorted().distinct().collect(toList());
    }

    @GET @Path("/stages/{name}") public Stage getStage(@PathParam("name") String name) {
        return clusters.stream()
                       .flatMap(Cluster::stages)
                       .filter(stage -> stage.getName().equals(name))
                       .findFirst()
                       .orElseThrow(() -> new NotFoundException("stage not found: '" + name + "'"));
    }


    @GET @Path("/deployments") public List<Deployment> getDeployments() {
        return clusters.stream().flatMap(this::fromCluster).collect(toList());
    }

    private Stream<Deployment> fromCluster(Cluster cluster) {
        return cluster.stages().flatMap(stage -> stage.nodes(cluster)).flatMap(controller::fetchDeploymentsOn);
    }

    @GET @Path("/deployments/{id}") public GetDeploymentResponse getDeployment(@PathParam("id") DeploymentId id) {
        ClusterNode node = id.node(clusters);
        Deployment deployment = controller
                .fetchDeploymentsOn(node)
                .filter(id::matchName)
                .findFirst()
                .orElseThrow(() -> new DeployableNotFoundException(id.deploymentName(), node));
        List<Version> available = controller.fetchVersions(node, deployment);
        return GetDeploymentResponse
                .builder()
                .id(id)
                .available(available)
                .build();
    }


    public static class DeployableNotFoundException extends BadRequestException {
        public DeployableNotFoundException(String deployableName, ClusterNode node) {
            super("deployable '" + deployableName + "' not found on '" + node.id() + "'");
        }
    }

    @Data
    @Builder
    public static class GetDeploymentResponse {
        private DeploymentId id;
        private List<Version> available;
    }


    @POST @Path("/deployments/{id}") public void postDeployments(
            @PathParam("id") DeploymentId id,
            @FormParam("version") String version,
            @FormParam("remove") DeploymentId remove) {
        if (version != null)
            controller.deploy(id.node(clusters).deployerUri(), id.deploymentName(), version);
        if (remove != null)
            controller.undeploy(remove.node(clusters).deployerUri(), remove.deploymentName());
    }
}
