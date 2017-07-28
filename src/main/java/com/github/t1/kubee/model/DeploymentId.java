package com.github.t1.kubee.model;

import lombok.Value;

import javax.ws.rs.BadRequestException;
import java.util.List;

@Value
public class DeploymentId {
    public final String value;

    @Override public String toString() { return value; }

    public String deploymentName() { return split()[4]; }

    public ClusterNode node(List<Cluster> clusters) {
        String[] split = split();
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
