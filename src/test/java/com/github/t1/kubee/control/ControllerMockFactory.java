package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterTest;

import java.util.List;

public class ControllerMockFactory {
    public static Controller createWithClusters() { return create(ClusterTest.readClusterConfig()); }

    public static Controller create(List<Cluster> clusters) {
        Controller controller = new Controller();
        controller.clusters = clusters;
        return controller;
    }
}
