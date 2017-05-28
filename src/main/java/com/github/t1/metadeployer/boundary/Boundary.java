package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.metadeployer.model.*;

import javax.ejb.Stateless;
import javax.inject.Inject;
import javax.ws.rs.*;
import java.net.URI;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static java.util.stream.Collectors.*;

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
        List<Deployable> deployables = fetchDeployablesFrom(node.uri());
        return deployables.stream().map(deployable ->
                Deployment.builder()
                          .cluster(node.getCluster())
                          .stage(node.getStage())
                          .node(node.getIndex())
                          .name(deployable.getName())
                          .groupId(orUnknown(deployable.getGroupId()))
                          .artifactId(orUnknown(deployable.getArtifactId()))
                          .version(orUnknown(deployable.getVersion()))
                          .type(orUnknown(deployable.getType()))
                          .error(deployable.getError())
                          .build());
    }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    private List<Deployable> fetchDeployablesFrom(URI uri) {
        try {
            return deployer.fetchDeployablesOn(uri);
        } catch (Exception e) {
            return singletonList(Deployable
                    .builder()
                    .name("?")
                    .groupId("unknown")
                    .artifactId("unknown")
                    .type("unknown")
                    .version("unknown")
                    .error(errorString(e))
                    .build());
        }
    }

    private static String errorString(Throwable e) {
        while (e.getCause() != null)
            e = e.getCause();
        String out = e.toString();
        while (out.startsWith(ExecutionException.class.getName() + ": ")
                || out.startsWith(RuntimeException.class.getName() + ": "))
            out = out.substring(out.indexOf(": ") + 2);
        return out;
    }
}
