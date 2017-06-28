package com.github.t1.metadeployer.model;

import lombok.Value;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;

import static java.lang.String.*;

/**
 * A JVM, i.e. one node of one stage of a cluster
 */
@Value
public class ClusterNode {
    Cluster cluster;
    Stage stage;
    int index;

    @Override public String toString() { return id(); }

    public URI deployerUri() { return UriBuilder.fromUri(uri()).path("/" + stage.getPath()).build(); }

    public URI uri() {
        return URI.create(format("http://%s%s%s%s%s:%d",
                stage.getPrefix(), cluster.getSimpleName(), stage.getSuffix(), stage.formattedIndex(index),
                (cluster.getDomainName().isEmpty()) ? "" : "." + cluster.getDomainName(),
                cluster.getSlot().getHttp()));
    }

    public boolean matchStageNameAndIndex(ClusterNode that) {
        return this.stage.getName().equals(that.stage.getName()) && this.index == that.index;
    }

    public String id() { return cluster.id() + ":" + stage.getName() + ":" + index; }

    public static ClusterNode fromId(String id, List<Cluster> clusters) {
        String[] split = id.split(":");
        String clusterName = split[0];
        String slotName = split[1];
        String stageName = split[2];
        int index = Integer.valueOf(split[3]);

        Cluster cluster = clusters.stream()
                                  .filter(c -> c.getSimpleName().equals(clusterName))
                                  .filter(c -> c.getSlot().getName().equals(slotName))
                                  .findFirst()
                                  .orElseThrow(() -> new ClusterNotFoundException(clusterName));

        Stage stage = cluster.stage(stageName)
                             .orElseThrow(() -> new StageNotFoundException(stageName));

        return cluster.node(stage, index);
    }

    public static class ClusterNotFoundException extends BadRequestException {
        public ClusterNotFoundException(String clusterName) { super("cluster not found: '" + clusterName + "'"); }
    }

    public static class StageNotFoundException extends BadRequestException {
        public StageNotFoundException(String stageName) { super("stage not found: '" + stageName + "'"); }
    }
}
