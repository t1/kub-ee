package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatusGateway {
    private Path dockerComposeDir;

    public ClusterStatus clusterStatus(Cluster cluster) {
        requireNonNull(dockerComposeDir, "no docker compose dir configured");
        return new ClusterStatus(cluster, dockerComposeDir);
    }
}
