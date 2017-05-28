package com.github.t1.metadeployer.model;

import lombok.*;

@Value
@Builder
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
                + "|" + cluster.getName() + ":" + stage.getName() + ":" + node
                + ((error == null) ? "" : "|error=" + error);
    }
}
