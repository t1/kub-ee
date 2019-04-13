package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.Clusters;
import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.health.HealthGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.entity.ClusterTest.DEV;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    static final String APPLICATION_NAME = "dummy-app";
    static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().name(APPLICATION_NAME).node(DEV01).groupId("app-group").artifactId("app-artifact").version("1.0.1").build();

    Controller controller = new Controller();
    Clusters clusters;
    DeployerGateway deployer;
    HealthGateway healthGateway;

    private Function<Stage, Ingress> originalIngressBuilder;
    Ingress ingress = mock(Ingress.class);

    @BeforeEach
    public void setUp() {
        originalIngressBuilder = Ingress.BUILDER;
        Ingress.BUILDER = stage -> ingress;

        this.clusters = controller.clusters = mock(Clusters.class);
        this.deployer = controller.deployer = mock(DeployerGateway.class);
        this.healthGateway = controller.healthGateway = mock(HealthGateway.class);

        given(clusters.stream()).will(i -> Stream.of(CLUSTERS));
    }

    @AfterEach
    void tearDown() {
        Ingress.BUILDER = originalIngressBuilder;
    }
}
