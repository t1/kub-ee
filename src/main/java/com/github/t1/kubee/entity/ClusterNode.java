package com.github.t1.kubee.entity;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.Comparator;

import static com.github.t1.kubee.entity.DeploymentStatus.running;

/**
 * A JVM, i.e. one node of one stage of a cluster
 */
@Value
public class ClusterNode implements Comparable<ClusterNode> {
    Cluster cluster;
    Stage stage;
    int number;

    @Override public String toString() { return id(); }

    @Override public int compareTo(@NotNull ClusterNode that) {
        return Comparator.comparing(ClusterNode::getCluster)
            .thenComparing(ClusterNode::getStage)
            .thenComparing(ClusterNode::getNumber)
            .compare(this, that);
    }

    public URI deployerUri() { return UriBuilder.fromUri(uri()).path("/" + stage.getPath()).build(); }

    public URI uri() { return URI.create("http://" + host() + ":" + port()); }

    public String host() { return stage.host(cluster, number); }

    public int port() { return cluster.getSlot().getHttp(); }

    public String id() { return cluster.id() + ":" + stage.getName() + ":" + number; }

    public Endpoint endpoint() { return new Endpoint(host(), port()); }

    public String serviceName() { return stage.serviceName(cluster); }

    public boolean matchStageNameAndIndex(ClusterNode that) {
        return this.stage.getName().equals(that.stage.getName()) && this.number == that.number;
    }

    public DeploymentStatus getStatusOfApp(String name) {
        return getStage().getStatus().getOrDefault(number + ":" + name, running);
    }
}
