package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.Script;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@Log
@NoArgsConstructor(force = true)
public class ClusterStatus {
    private final @NonNull Cluster cluster;
    private final @NonNull Path dockerComposeDir;
    private final @NonNull ClusterEndpoints clusterEndpoints;

    ClusterStatus(
        @NonNull Cluster cluster,
        @NonNull Path dockerComposeDir
    ) {
        this.cluster = cluster;
        this.dockerComposeDir = dockerComposeDir;
        this.clusterEndpoints = new ClusterEndpoints(cluster, dockerComposeDir);
    }

    @Override public String toString() { return "cluster [" + cluster.getHost() + "]: " + clusterEndpoints; }

    public Integer exposedPort(ClusterNode node) {
        return endpoints(node.getStage())
            .filter(endpoint -> endpoint.getHost().equals(node.host()))
            .findFirst()
            .map(Endpoint::getPort)
            .orElse(null);
    }

    public Integer exposedPort(String host) {
        return endpoints()
            .filter(endpoint -> endpoint.getHost().equals(host))
            .findFirst()
            .map(Endpoint::getPort)
            .orElse(null);
    }

    public Stream<Endpoint> endpoints(Stage stage) {
        return clusterEndpoints.get(stage).stream();
    }

    public Stream<Endpoint> endpoints() {
        return cluster.stages().map(clusterEndpoints::get).flatMap(Collection::stream);
    }

    public void scale(Stage stage) {
        List<Endpoint> currentEndpoints = this.clusterEndpoints.get(stage);
        if (currentEndpoints.size() == stage.getCount()) {
            log.fine("'" + stage.getName() + "' is already scaled to " + stage.getCount());
            return;
        }
        String serviceName = stage.serviceName(cluster);
        log.info("Scale '" + serviceName + "' from " + currentEndpoints.size() + " to " + stage.getCount());
        new Script("docker-compose up --no-color --quiet-pull --detach --scale " + serviceName + "=" + stage.getCount())
            .workingDirectory(dockerComposeDir)
            .run();
        clusterEndpoints.refreshEndpointsFor(stage);
    }
}
