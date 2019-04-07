package com.github.t1.kubee.boundary.gateway.deployer;

import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway.Deployable;
import com.github.t1.testtools.OrderedJUnitRunner;
import com.github.t1.testtools.WebArchiveBuilder;
import com.github.t1.testtools.WildflySwarmTestRule;
import lombok.SneakyThrows;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(OrderedJUnitRunner.class)
public class DeployerGatewayIT {
    @ClassRule public static WildflySwarmTestRule swarm = new WildflySwarmTestRule();

    @BeforeClass @SneakyThrows public static void deployDeployerMock() {
        swarm.deploy(new WebArchiveBuilder("deployer.war")
                .with(DeployerMock.class, DeployerMockJaxRs.class)
                .print().build());
    }

    @Test
    public void shouldFetchDeployables() {
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