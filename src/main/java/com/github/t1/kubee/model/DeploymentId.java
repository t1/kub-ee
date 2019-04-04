package com.github.t1.kubee.model;

import lombok.Value;

import javax.ws.rs.BadRequestException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.http.ProblemDetail.badRequest;

/**
 * A String made out of these components delimited by colons:<ol>
 * <li>cluster name</li>
 * <li>slot name</li>
 * <li>stage name</li>
 * <li>node index</li>
 * <li>deployment name</li>
 * </ol>
 */
@Value
public class DeploymentId {
    public final String value;

    @Override public String toString() { return value; }

    private String getStageName() { return split()[2]; }

    public String deploymentName() { return split()[4]; }

    public Stage stage(List<Cluster> clusters) {
        String name = getStageName();
        return clusters.stream().flatMap(cluster -> asStream(cluster.stage(name))).findAny()
                       .orElseThrow(() -> badRequest().detail("stage not found: " + name).exception());
    }

    private static <T> Stream<T> asStream(@SuppressWarnings("OptionalUsedAsFieldOrParameterType") Optional<T> opt) {
        return opt.map(Stream::of).orElseGet(Stream::empty);
    }

    public ClusterNode node(Stream<Cluster> clusters) {
        String[] split = split();
        String clusterName = split[0];
        String slotName = split[1];
        String stageName = split[2];
        int index = Integer.valueOf(split[3]);

        Cluster cluster = clusters.filter(c -> c.getSimpleName().equals(clusterName))
                                  .filter(c -> c.getSlot().getName().equals(slotName))
                                  .findFirst()
                                  .orElseThrow(() -> new ClusterNotFoundException(clusterName));

        Stage stage = cluster.stage(stageName)
                             .orElseThrow(() -> new StageNotFoundException(stageName));

        return cluster.node(stage, index);
    }

    private String[] split() {
        return value.split(":");
    }

    public boolean matchName(Deployment deployment) {
        return deployment.getName().equals(deploymentName());
    }

    public static class ClusterNotFoundException extends BadRequestException {
        public ClusterNotFoundException(String clusterName) { super("cluster not found: '" + clusterName + "'"); }
    }

    public static class StageNotFoundException extends BadRequestException {
        public StageNotFoundException(String stageName) { super("stage not found: '" + stageName + "'"); }
    }
}
