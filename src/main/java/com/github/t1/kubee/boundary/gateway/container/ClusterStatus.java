package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;

@Log
@NoArgsConstructor(force = true)
public class ClusterStatus {
    private final @NonNull Cluster cluster;
    private final @NonNull DockerCommands dockerCommands;

    ClusterStatus(
        @NonNull Cluster cluster,
        @NonNull Path dockerComposeDir
    ) {
        this.cluster = cluster;
        this.dockerCommands = new DockerCommands(dockerComposeDir);
    }

    @Override public String toString() { return "cluster [" + cluster.getHost() + "]"; }

    public Integer exposedPort(Stage stage, String host) {
        return endpoints(stage)
            .filter(endpoint -> endpoint.getHost().equals(host))
            .findAny() // assuming there can be only one
            .map(Endpoint::getPort)
            .orElse(null);
    }

    public Stream<Endpoint> endpoints(Stage stage) {
        AtomicInteger nodeNumber = new AtomicInteger(1);
        return dockerPorts(serviceName(stage)).stream()
            .map(port -> {
                String host = stage.host(cluster, nodeNumber.getAndIncrement());
                return new Endpoint(host, port);
            });
    }

    public Stream<Endpoint> endpoints() { return cluster.stages().flatMap(this::endpoints); }

    public void scale() {
        Map<String, Integer> scales = new LinkedHashMap<>();
        for (Stage stage : cluster.getStages()) {
            String serviceName = serviceName(stage);
            List<Integer> currentPorts = dockerPorts(serviceName);
            if (currentPorts.size() == stage.getCount()) {
                log.fine("'" + stage.getName() + "' is already scaled to " + stage.getCount());
            } else {
                log.info("Scale '" + serviceName + "' from " + currentPorts.size() + " to " + stage.getCount());
                scales.put(serviceName, stage.getCount());
            }
        }
        if (scales.isEmpty()) {
            log.info("no scaling for " + cluster.id() + " necessary");
        } else {
            dockerCommands.scale(scales);
            log.info("scale " + cluster.id() + " complete");
        }
    }

    private List<Integer> dockerPorts(String serviceName) {
        return dockerCommands.getDockerPorts().getOrDefault(serviceName, emptyList());
    }

    private String serviceName(Stage stage) {
        return stage.serviceName(cluster);
    }
}
