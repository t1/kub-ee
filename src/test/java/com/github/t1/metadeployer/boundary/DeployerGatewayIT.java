package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.*;

public class DeployerGatewayIT {
    @Test
    public void shouldGetDeployables() throws Exception {
        CompletableFuture<List<Deployable>> future = new DeployerGateway()
                .getDeployments(URI.create("http://localhost:8080/deployer"));

        List<Deployable> deployables = future.get(10, SECONDS);

        assertThat(deployables).containsOnly(
                emptyChecksumDeployable("deployer"),
                emptyChecksumDeployable("meta-deployer")
        );
    }
}
