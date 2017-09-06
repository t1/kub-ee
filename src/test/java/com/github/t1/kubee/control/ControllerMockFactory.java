package com.github.t1.kubee.control;

import com.github.t1.kubee.model.*;

import java.util.List;

public class ControllerMockFactory {
    public static Controller createWithClusters() { return create(ClusterTest.readClusterConfig().clusters()); }

    public static Controller create(List<Cluster> clusters) {
        Controller controller = new Controller();
        controller.clusters = clusters;
        return controller;
    }
}
