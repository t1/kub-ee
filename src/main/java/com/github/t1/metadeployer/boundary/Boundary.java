package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.net.*;
import java.util.List;
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

    @Path("/clusters")
    @GET public List<Cluster> getClusters() { return clusters; }

    @GET public List<Deployment> get() {
        return clusters.stream().flatMap(this::fromCluster).collect(toList());
    }

    private Stream<Deployment> fromCluster(Cluster cluster) {
        return cluster.stages().flatMap(stage -> stage.nodes(cluster)).flatMap(this::fetch);
    }

    private Stream<Deployment> fetch(ClusterNode node) {
        List<Deployable> deployables = fetchDeployablesFrom(node);
        //noinspection unchecked
        log.debug("fetched deployables from {}:", node);
        return deployables.stream()
                          .peek(deployable -> log.debug("  - {}", deployable.getName()))
                          .map(deployable ->
                                  Deployment.builder()
                                            .clusterNode(node)
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
        URI uri = node.uri();
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
