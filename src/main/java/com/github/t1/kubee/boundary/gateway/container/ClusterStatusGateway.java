package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Optional;

@NoArgsConstructor
@SuppressWarnings("CdiInjectionPointsInspection")
public class ClusterStatusGateway {
    @Inject @ConfigProperty private Optional<Path> dockerComposeDir;

    public ClusterStatusGateway(Path dockerComposeDir) {
        this.dockerComposeDir = Optional.ofNullable(dockerComposeDir);
    }

    public ClusterStatus clusterStatus(Cluster cluster) {
        if (!dockerComposeDir.isPresent())
            throw new RuntimeException("no docker compose dir configured");
        return new ClusterStatus(cluster, dockerComposeDir.get());
    }
}
