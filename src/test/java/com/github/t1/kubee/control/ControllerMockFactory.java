package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.Clusters;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterTest;

import java.util.List;
import java.util.stream.Stream;

public class ControllerMockFactory {
    public static Controller createWithClusters() { return create(ClusterTest.readClusterConfig()); }

    public static Controller create(List<Cluster> clusters) {
        Controller controller = new Controller();
        controller.clusters = new Clusters() {
            @Override public Stream<Cluster> stream() { return clusters.stream(); }
        };
        return controller;
    }
}
