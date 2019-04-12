package com.github.t1.kubee.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.Comparator;

/**
 * Meta data about an application deployed on a cluster node
 */
@Value
@Builder(toBuilder = true)
public class Deployment implements Comparable<Deployment> {
    /** the name and context root of the deployment */
    String name;

    /** maven coordinates */
    String groupId, artifactId, version;

    /** the bundle type of the artifact, e.g. `war` */
    String type;

    /** an error message received when looking up the maven coordinates */
    String error;

    /** the cluster node that this deployment is on */
    ClusterNode node;

    @Override public String toString() {
        return "Deployment(" + name + ":" + type
            + "|" + groupId + ":" + artifactId + ":" + version
            + "|" + node
            + (hasError() ? "|error=" + error : "")
            + ")";
    }

    public boolean hasError() { return error != null && !error.isEmpty(); }

    @JsonIgnore public String getSlotName() { return node.getCluster().getSlot().getName(); }

    public String id() { return node.id() + ":" + name; }

    public String gav() { return groupId + ":" + artifactId + ":" + version; }

    @Override public int compareTo(@NonNull Deployment that) {
        return Comparator.comparing(Deployment::getName).compare(this, that);
    }
}
