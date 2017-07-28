package com.github.t1.kubee.model;

import lombok.Value;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;

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
}
