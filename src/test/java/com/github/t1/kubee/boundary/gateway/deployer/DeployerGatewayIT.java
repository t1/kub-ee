package com.github.t1.kubee.boundary.gateway.deployer;

import com.github.t1.testtools.WebArchiveBuilder;
import com.github.t1.testtools.WildflySwarmTestExtension;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled
class DeployerGatewayIT {
    // @RegisterExtension
    public static WildflySwarmTestExtension swarm = null; //new WildflySwarmTestExtension();

    @BeforeAll @SneakyThrows static void deployDeployerMock() {
        swarm.deploy(new WebArchiveBuilder("deployer.war")
            .with(DeployerMock.class, DeployerMockJaxRs.class)
            .print().build());
    }

    @Test void shouldFetchDeployables() {
        List<Deployable> deployables = new DeployerGateway().fetchDeploymentsFrom(swarm.baseUri());

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
