package com.github.t1.metadeployer.model;

import lombok.*;

@Value
@Builder(toBuilder = true)
public class Deployment {
    ClusterNode clusterNode;

    String name;
    String groupId;
    String artifactId;
    String type;
    String version;
    String error;

    @Override public String toString() {
        return "Deployment(" + name + ":" + type
                + "|" + groupId + ":" + artifactId + ":" + version
                + "|" + clusterNode
                + (hasError() ? "" : "|error=" + error);
    }

    public boolean hasError() { return error != null && !error.isEmpty(); }
}
