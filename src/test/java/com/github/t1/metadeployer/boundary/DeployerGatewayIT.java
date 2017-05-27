package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import java.net.URI;
import java.util.List;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static org.assertj.core.api.Assertions.*;

public class DeployerGatewayIT {
    @Test
    public void shouldGetDeployables() throws Exception {
        List<Deployable> deployables = new DeployerGateway()
                .fetchDeployments(URI.create("http://localhost:8080/deployer"));

        assertThat(deployables).containsOnly(
                emptyChecksumDeployable("deployer"),
                emptyChecksumDeployable("meta-deployer")
        );
    }
}
