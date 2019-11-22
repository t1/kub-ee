package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
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

import static com.github.t1.kubee.TestData.ALL_CLUSTERS;
import static com.github.t1.kubee.TestData.APPLICATION_NAME;
import static com.github.t1.kubee.TestData.CLUSTER_A1;
import static com.github.t1.kubee.TestData.DEV;
import static com.github.t1.kubee.TestData.VERSION_101;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    static final ClusterNode DEV01 = CLUSTER_A1.node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().node(DEV01).name(APPLICATION_NAME).groupId("app-group").artifactId("app-artifact").version(VERSION_101).build();

    Controller controller = new Controller();
    ClusterStore clusterStore;
    DeployerGateway deployer;
    HealthGateway healthGateway;

    private Function<Stage, Ingress> originalIngressBuilder;
    Ingress ingress = mock(Ingress.class);

    @BeforeEach
    public void setUp() {
        originalIngressBuilder = Ingress.BUILDER;
        Ingress.BUILDER = stage -> ingress;

        this.clusterStore = controller.clusterStore = mock(ClusterStore.class);
        this.deployer = controller.deployer = mock(DeployerGateway.class);
        this.healthGateway = controller.healthGateway = mock(HealthGateway.class);

        given(clusterStore.clusters()).will(i -> Stream.of(ALL_CLUSTERS));
    }

    @AfterEach
    void tearDown() {
        Ingress.BUILDER = originalIngressBuilder;
    }
}
