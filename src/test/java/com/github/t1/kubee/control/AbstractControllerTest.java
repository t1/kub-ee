package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.health.HealthGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Function;

import static com.github.t1.kubee.entity.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.entity.ClusterTest.DEV;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().name("foo").node(DEV01).groupId("foo-group").artifactId("foo-artifact").version("1.0.1").build();

    Controller controller = new Controller();

    private Function<Stage, Ingress> originalIngressBuilder;
    Ingress ingress = mock(Ingress.class);

    @BeforeEach
    public void setUp() {
        originalIngressBuilder = Ingress.BUILDER;
        Ingress.BUILDER = stage -> ingress;

        controller.clusters = asList(CLUSTERS);
        controller.deployer = mock(DeployerGateway.class);
        controller.healthGateway = mock(HealthGateway.class);
    }

    @AfterEach
    void tearDown() {
        Ingress.BUILDER = originalIngressBuilder;
    }
}
