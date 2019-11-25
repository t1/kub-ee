package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

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

    @Override public String toString() { return "cluster [" + cluster.getHost() + "]: " + dockerCommands; }

    public Integer exposedPort(Stage stage, String host) {
        return endpoints(stage)
            .filter(endpoint -> endpoint.getHost().equals(host))
            .findAny() // assuming there can't be only one
            .map(Endpoint::getPort)
            .orElse(null);
    }

    public Stream<Endpoint> endpoints(Stage stage) {
        AtomicInteger nodeNumber = new AtomicInteger(1);
        return dockerCommands
            .getDockerPorts(serviceName(stage))
            .map(port -> {
                String host = stage.host(cluster, nodeNumber.getAndIncrement());
                return new Endpoint(host, port);
            });
    }

    public Stream<Endpoint> endpoints() { return cluster.stages().flatMap(this::endpoints); }

    public void scale(Stage stage) {
        String serviceName = serviceName(stage);
        List<Integer> currentPorts = this.dockerCommands.getDockerPorts(serviceName).collect(toList());
        if (currentPorts.size() == stage.getCount()) {
            log.fine("'" + stage.getName() + "' is already scaled to " + stage.getCount());
        } else {
            log.info("Scale '" + serviceName + "' from " + currentPorts.size() + " to " + stage.getCount());
            dockerCommands.scale(serviceName + "=" + stage.getCount());
        }
    }

    private String serviceName(Stage stage) {
        return stage.serviceName(cluster);
    }
}
