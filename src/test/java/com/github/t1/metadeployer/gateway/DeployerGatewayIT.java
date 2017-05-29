package com.github.t1.metadeployer.gateway;

import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

public class DeployerGatewayIT {
    @Test
    public void shouldFetchDeployables() throws Exception {
        List<Deployable> deployables = new DeployerGateway().fetchDeployablesOn(URI.create("http://localhost:8080"));

        assertThat(deployables).contains(
                Deployable.builder()
                          .name("deployer")
                          .groupId("com.github.t1")
                          .artifactId("deployer")
                          .type("war")
                          .version("2.9.2")
                          .build(),
                Deployable.builder()
                          .name("meta-deployer")
                          .groupId("unknown")
                          .artifactId("unknown")
                          .type("unknown")
                          .version("unknown")
                          .error("empty checksum")
                          .build()
        );
    }
}
