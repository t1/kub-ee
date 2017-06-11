package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

@Slf4j
@Path("/")
@Stateless
public class Boundary {
    @Inject List<Cluster> clusters;

    DeployerGateway deployer = new DeployerGateway();

    @GET public Map<String, URI> getLinks(@Context UriInfo uriInfo) {
        Map<String, URI> map = new LinkedHashMap<>();
        map.put("clusters", linkForMethod(uriInfo, "getClusters"));
        map.put("slots", linkForMethod(uriInfo, "getSlots"));
        map.put("stages", linkForMethod(uriInfo, "getStages"));
        map.put("applications", linkForMethod(uriInfo, "getApplications"));
        return map;
    }

    private URI linkForMethod(@Context UriInfo uriInfo, String method) {
        return uriInfo.getBaseUriBuilder().path(Boundary.class, method).build();
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
        return clusters.stream().map(Cluster::getSlot).distinct().collect(toList());
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

    @Path("/applications")
    @GET public List<Deployment> getApplications() {
        return clusters.stream().flatMap(this::fromCluster).collect(toList());
    }

    private Stream<Deployment> fromCluster(Cluster cluster) {
        return cluster.stages().flatMap(stage -> stage.nodes(cluster)).flatMap(this::fetch);
    }

    private Stream<Deployment> fetch(ClusterNode node) {
        log.debug("fetch deployables from {}:", node);
        return fetchDeployablesFrom(node)
                .stream()
                .peek(deployable -> log.debug("  - {}", deployable.getName()))
                .map(deployable ->
                        Deployment.builder()
                                  .node(node)
                                  .name(deployable.getName())
                                  .groupId(orUnknown(deployable.getGroupId()))
                                  .artifactId(orUnknown(deployable.getArtifactId()))
                                  .version(orUnknown(deployable.getVersion()))
                                  .type(orUnknown(deployable.getType()))
                                  .error(deployable.getError())
                                  .build());
    }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    private List<Deployable> fetchDeployablesFrom(ClusterNode node) {
        URI uri = node.deployerUri();
        try {
            return deployer.fetchDeployablesOn(uri);
        } catch (Exception e) {
            String error = errorString(e);
            log.debug("deployer not found on {}: {}: {}", node, uri, error);
            return singletonList(Deployable
                    .builder()
                    .name("-")
                    .groupId("-")
                    .artifactId("-")
                    .type("-")
                    .version("-")
                    .error(error)
                    .build());
        }
    }

    private static String errorString(Throwable e) {
        while (e.getCause() != null)
            e = e.getCause();
        String out = e.toString();
        while (out.startsWith(ExecutionException.class.getName() + ": ")
                || out.startsWith(ConnectException.class.getName() + ": ")
                || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        if (out.endsWith(UNKNOWN_HOST_SUFFIX))
            out = out.substring(0, out.length() - UNKNOWN_HOST_SUFFIX.length());
        if (out.startsWith(UnknownHostException.class.getName() + ": "))
            out = "unknown host: " + out.substring(out.indexOf(": ") + 2);
        if (out.equals("Connection refused (Connection refused)"))
            out = "connection refused";
        return out;
    }

    private static final String UNKNOWN_HOST_SUFFIX = ": nodename nor servname provided, or not known";
}
