package com.github.t1.metadeployer.model;

import lombok.*;

@Value
@Builder(toBuilder = true)
public class Deployment {
    Cluster cluster;
    Stage stage;
    int node;

    String name;
    String groupId;
    String artifactId;
    String type;
    String version;
    String error;

    @Override public String toString() {
        return "Deployment(" + name + ":" + type
                + "|" + groupId + ":" + artifactId + ":" + version
                + "|" + stage.getPrefix() + "`" + cluster.getName() + "`" + stage.getSuffix() + ":" + cluster.getPort()
                + "|" + stage.getName() + ":" + stage.getCount() + ":" + stage.getIndexLength() + ":" + node
                + (hasError() ? "" : "|error=" + error);
    }

    public boolean hasError() { return error != null && !error.isEmpty(); }

    public boolean isOn(ClusterNode node) {
        return cluster.equals(node.getCluster()) && stage.equals(node.getStage()) && this.node == node.getIndex();
    }
}
