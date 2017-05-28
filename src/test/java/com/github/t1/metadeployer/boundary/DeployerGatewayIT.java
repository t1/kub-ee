package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class DeployerGatewayIT {
    public static Deployable emptyChecksumDeployable(String name) {
        return Deployable.builder()
                         .name(name)
                         .groupId("unknown")
                         .artifactId("unknown")
                         .type("unknown")
                         .version("unknown")
                         .error("empty checksum")
                         .build();
    }

    @Test
    public void shouldFetchDeployables() throws Exception {
        List<Deployable> deployables = new DeployerGateway().fetchDeployablesOn(URI.create("http://localhost:8080"));

        assertThat(deployables).containsOnly(
                emptyChecksumDeployable("deployer"),
                emptyChecksumDeployable("meta-deployer")
        );
    }
}
