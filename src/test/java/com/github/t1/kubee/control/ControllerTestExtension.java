package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.health.HealthGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.boundary.gateway.ingress.IngressFactory;
import com.github.t1.kubee.entity.Stage;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.ALL_CLUSTERS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

public class ControllerTestExtension implements Extension, BeforeEachCallback, AfterEachCallback {
    Controller controller = new Controller();
    ClusterStore clusterStore = mock(ClusterStore.class);
    DeployerGateway deployer = mock(DeployerGateway.class);
    HealthGateway healthGateway = mock(HealthGateway.class);
    Ingress ingress = mock(Ingress.class);

    private Function<Stage, Ingress> originalIngressBuilder;

    @Override public void beforeEach(ExtensionContext context) {
        originalIngressBuilder = IngressFactory.BUILDER;
        IngressFactory.BUILDER = stage -> ingress;

        controller.clusterStore = this.clusterStore;
        controller.deployer = this.deployer;
        controller.healthGateway = this.healthGateway;

        given(clusterStore.clusters()).will(i -> Stream.of(ALL_CLUSTERS));
    }

    @Override public void afterEach(ExtensionContext context) {
        IngressFactory.BUILDER = originalIngressBuilder;
    }
}
