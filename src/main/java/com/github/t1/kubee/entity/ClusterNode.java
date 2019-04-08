package com.github.t1.kubee.entity;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Comparator;

/**
 * A JVM, i.e. one node of one stage of a cluster
 */
@Value
public class ClusterNode implements Comparable<ClusterNode> {
    Cluster cluster;
    Stage stage;
    int index;

    @Override public String toString() { return id(); }

    @Override public int compareTo(@NotNull ClusterNode that) {
        return Comparator.comparing(ClusterNode::getCluster)
            .thenComparing(ClusterNode::getStage)
            .thenComparing(ClusterNode::getIndex)
            .compare(this, that);
    }

    public URI deployerUri() { return UriBuilder.fromUri(uri()).path("/" + stage.getPath()).build(); }

    public URI uri() { return URI.create("http://" + host() + ":" + port()); }

    public String host() {
        return ""
            + stage.nodeBaseName(cluster)
            + stage.formattedIndex(index)
            + ((cluster.getDomainName().isEmpty()) ? "" : "." + cluster.getDomainName());
    }

    public int port() { return cluster.getSlot().getHttp(); }

    public String id() { return cluster.id() + ":" + stage.getName() + ":" + index; }

    public Endpoint endpoint() { return new Endpoint(host(), port()); }

    public boolean matchStageNameAndIndex(ClusterNode that) {
        return this.stage.getName().equals(that.stage.getName()) && this.index == that.index;
    }
}
