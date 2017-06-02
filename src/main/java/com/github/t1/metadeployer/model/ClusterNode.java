package com.github.t1.metadeployer.model;

import lombok.Value;

import java.net.URI;

import static java.lang.String.*;

@Value
public class ClusterNode {
    Cluster cluster;
    Stage stage;
    int index;

    @Override public String toString() {
        return ((cluster == null) ? "" : cluster.getHost() + ":slot-" + cluster.getSlot().getName())
                + ":" + stage.getName() + ":node-" + index;
    }

    public URI uri() {
        return URI.create(format("http://%s%s%s%s.%s:%d",
                stage.getPrefix(), cluster.getSimpleName(), stage.getSuffix(), stage.formattedIndex(index),
                cluster.getDomainName(), cluster.getSlot().getHttp()));
    }

    public boolean matchStageNameAndIndex(ClusterNode that) {
        return this.stage.getName().equals(that.stage.getName()) && this.index == that.index;
    }

    public String id() { return cluster.id() + ":" + stage.getName() + ":" + index; }
}