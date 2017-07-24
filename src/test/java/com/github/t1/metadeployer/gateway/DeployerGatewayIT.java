package com.github.t1.metadeployer.gateway;

import com.github.t1.metadeployer.gateway.deployer.DeployerGateway;
import com.github.t1.metadeployer.gateway.deployer.DeployerGateway.Deployable;
import com.github.t1.testtools.*;
import lombok.SneakyThrows;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class DeployerGatewayIT {
    @ClassRule public static WildflySwarmTestRule swarm = new WildflySwarmTestRule();

    @BeforeClass @SneakyThrows public static void deployDeployerMock() {
        swarm.deploy(new WebArchiveBuilder("deployer.war")
                .with(DeployerMock.class, DeployerMockJaxRs.class)
                .print().build());
    }

    @Test
    public void shouldFetchDeployables() throws Exception {
        List<Deployable> deployables = new DeployerGateway().fetchDeployablesOn(swarm.baseUri());

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
