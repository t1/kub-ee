package com.github.t1.kubee.boundary.gateway.deployer;

import com.github.t1.jaxrsclienttest.JaxRsTestExtension;
import com.github.t1.kubee.tools.http.YamlHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeployerGatewayIT {
    @RegisterExtension static JaxRsTestExtension rest = new JaxRsTestExtension(new DeployerMock());

    @Test void shouldFetchDeployables() {
        List<Deployable> deployables = new DeployerGateway(new YamlHttpClient()).fetchDeploymentsFrom(rest.baseUri());

        assertThat(deployables).containsExactly(
            Deployable.builder()
                .name("deployer")
                .groupId("com.github.t1")
                .artifactId("deployer")
                .type("war")
                .version("2.9.2")
                .build(),
            Deployable.builder()
                .name("dummy")
                .groupId("com.github.t1")
                .artifactId("dummy")
                .type("war")
                .version("1.2.3")
                .build()
        );
    }
}
