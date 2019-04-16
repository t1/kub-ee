package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.boundary.config.DockerComposeDir;
import com.github.t1.kubee.entity.Cluster;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.inject.Inject;
import java.nio.file.Path;

@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatusGateway {
    @Inject @DockerComposeDir private Path dockerComposeDir;

    public ClusterStatus clusterStatus(Cluster cluster) {
        if (dockerComposeDir == null)
            throw new RuntimeException("no docker compose dir configured");
        return new ClusterStatus(cluster, dockerComposeDir);
    }
}
