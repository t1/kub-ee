package com.github.t1.kubee.boundary.rest;

import com.github.t1.kubee.control.Controller;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.DeploymentId;
import com.github.t1.kubee.entity.LoadBalancer;
import com.github.t1.kubee.entity.ReverseProxy;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.entity.Version;
import com.github.t1.log.Logged;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.http.ProblemDetail.badRequest;
import static com.github.t1.log.LogLevel.INFO;
import static java.util.Arrays.asList;
import static java.util.Locale.US;
import static java.util.stream.Collectors.toList;

@Logged(level = INFO)
@Slf4j
@Path("/")
@Stateless
public class Boundary {
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


    @GET @Path("/load-balancers") public List<LoadBalancer> getLoadBalancers() {
        return controller.loadBalancers(stages()).distinct().collect(toList());
    }

    @GET @Path("/reverse-proxies")
    public List<ReverseProxy> getReverseProxies() { return controller.reverseProxies(stages()).collect(toList()); }


    @GET @Path("/clusters") public List<Cluster> getClusters() { return controller.clusters().collect(toList()); }

    @GET @Path("/clusters/{name}") public Cluster getCluster(@PathParam("name") String name) {
        return controller.clusters()
            .filter(cluster -> cluster.getSimpleName().equals(name))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("cluster not found: '" + name + "'"));
    }


    @GET @Path("/slots") public List<Slot> getSlots() {
        return controller.clusters().map(Cluster::getSlot).sorted().distinct().collect(toList());
    }

    @GET @Path("/slots/{name}") public Slot getSlot(@PathParam("name") String name) {
        return controller.clusters()
            .map(Cluster::getSlot)
            .filter(slot -> slot.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("slot not found: '" + name + "'"));
    }


    @GET @Path("/stages") public List<Stage> getStages() { return stages().collect(toList()); }

    @GET @Path("/stages/{name}") public Stage getStage(@PathParam("name") String name) {
        return stages()
            .filter(stage -> stage.getName().equals(name))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("stage not found: '" + name + "'"));
    }

    private Stream<Stage> stages() { return controller.clusters().flatMap(Cluster::stages).sorted().distinct(); }


    @GET @Path("/deployments") public List<Deployment> getDeployments() {
        return controller.clusters().flatMap(this::deploymentsOnCluster).sorted().collect(toList());
    }

    private Stream<Deployment> deploymentsOnCluster(Cluster cluster) {
        return cluster.stages().flatMap(stage -> stage.nodes(cluster)).flatMap(controller::fetchDeploymentsOn);
    }

    @GET @Path("/deployments/{id}") public GetDeploymentResponse getDeployment(@PathParam("id") DeploymentId id) {
        ClusterNode node = id.node(controller.clusters());
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
        DeployableNotFoundException(String deployableName, ClusterNode node) {
            super("deployable '" + deployableName + "' not found on '" + node.id() + "'");
        }
    }

    @Data
    @Builder
    public static class GetDeploymentResponse {
        private DeploymentId id;
        private List<Version> available;
    }


    public enum DeploymentMode {deploy, balance, unbalance, undeploy}

    @POST @Path("/deployments/{id}") public void postDeployments(
        @PathParam("id") DeploymentId id,
        @FormParam("version") String version,
        @FormParam("mode") DeploymentMode mode) {
        if (id == null)
            throw badRequest().detail("id is a required parameter").exception();
        if (mode == null)
            throw badRequest().detail("mode is a required parameter").exception();
        switch (mode) {
            case deploy:
                if (version == null)
                    throw badRequest().detail("version is a required parameter when deploying").exception();
                controller.deploy(id, version);
                break;
            case balance:
                controller.balance(id);
                break;
            case unbalance:
                controller.unbalance(id);
                break;
            case undeploy:
                controller.undeploy(id);
                break;
        }
    }
}
