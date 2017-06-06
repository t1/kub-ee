package com.github.t1.metadeployer.gateway;

import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import com.github.t1.testtools.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@Slf4j
@RunWith(OrderedJUnitRunner.class)
public class DeployerGatewayIT {
    @ClassRule public static WildflySwarmTestRule deployer = new WildflySwarmTestRule();

    @BeforeClass @SneakyThrows public static void deployDeployerMock() {
        WebArchive deployer = ShrinkWrap.create(WebArchive.class, "deployer.war");
        deployer.addClasses(DeployerMockJaxRs.class, DeployerMock.class);
        log.info("---------------------\n{}\n---------------------", deployer.toString(true));
        DeployerGatewayIT.deployer.deploy(deployer);
    }

    @Test
    public void shouldFetchDeployables() throws Exception {
        List<Deployable> deployables = new DeployerGateway().fetchDeployablesOn(deployer.baseUri());

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
