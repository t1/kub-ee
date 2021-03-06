package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.health.HealthGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.control.Controller.UnexpectedAuditException;
import com.github.t1.kubee.entity.Audits;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.DeploymentId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;

import static com.github.t1.kubee.TestData.APPLICATION_NAME;
import static com.github.t1.kubee.TestData.CLUSTER_A1;
import static com.github.t1.kubee.TestData.CLUSTER_B2;
import static com.github.t1.kubee.TestData.DEV;
import static com.github.t1.kubee.TestData.VERSION_100;
import static com.github.t1.kubee.TestData.VERSION_101;
import static com.github.t1.kubee.TestData.VERSION_102;
import static com.github.t1.kubee.TestData.VERSION_103;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class DeployTest {
    private static final ClusterNode UNBALANCED_NODE = CLUSTER_B2.node("PROD", 2);
    private static final ClusterNode A1_DEV_1 = CLUSTER_A1.node(DEV, 1);
    private static final Deployment DEPLOYMENT = Deployment
        .builder().node(A1_DEV_1).name(APPLICATION_NAME).groupId("app-group").artifactId("app-artifact").version(VERSION_101).build();

    @RegisterExtension ControllerTestExtension extension = new ControllerTestExtension();
    private final Controller controller = extension.controller;
    private final DeployerGateway deployer = extension.deployer;
    private final HealthGateway healthGateway = extension.healthGateway;
    private final ClusterStore clusterStore = extension.clusterStore;
    private final Ingress ingress = extension.ingress;

    private String versionBefore = VERSION_100;
    private String versionAfter = VERSION_102;

    @BeforeEach void setup() {
        given(deployer.fetchVersion(A1_DEV_1, APPLICATION_NAME)).will(i -> versionBefore);
    }

    private void givenHealthy(Boolean value, Boolean... values) {
        given(healthGateway.fetch(A1_DEV_1, APPLICATION_NAME)).willReturn(value, values);
    }

    private BDDMyOngoingStubbing<Audits> givenDeploy() { return givenDeploy(versionAfter); }

    private BDDMyOngoingStubbing<Audits> givenDeploy(String version) {
        return given(deployer.deploy(A1_DEV_1, APPLICATION_NAME, version));
    }

    private BDDMyOngoingStubbing<Audits> givenUndeploy() {
        return given(deployer.undeploy(A1_DEV_1, APPLICATION_NAME));
    }

    private Audits audit(String operation, String oldVersion, String newVersion) {
        return Audits.parseYaml(""
            + "audits:\n"
            + "- !<deployable>\n"
            + "  operation: " + operation + "\n"
            + "  name: " + APPLICATION_NAME + "\n"
            + "  changes:\n"
            + ((oldVersion == null && newVersion == null) ? "" : "  - name: version\n")
            + ((oldVersion == null) ? "" : "    old-value: " + oldVersion + "\n")
            + ((newVersion == null) ? "" : "    new-value: " + newVersion + "\n")
            + "processState: running\n");
    }


    @AfterEach void assertNoMore() {
        verify(deployer, atLeast(0)).fetchVersion(A1_DEV_1, APPLICATION_NAME);
        verify(deployer, atLeast(0)).fetchVersion(UNBALANCED_NODE, APPLICATION_NAME);
        verify(clusterStore, atLeast(0)).clusters();
        verifyNoMoreInteractions(deployer, ingress, clusterStore);
    }


    @Test void shouldDeployNew() {
        versionBefore = null;
        givenHealthy(true);
        givenDeploy().willReturn(audit("add", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT.id(), versionAfter);

        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldDeployUpdate() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT.id(), versionAfter);

        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldRedeploy() {
        givenHealthy(true);
        givenUndeploy().willReturn(audit("remove", versionBefore, null));
        givenDeploy(versionBefore).willReturn(audit("add", null, versionBefore));

        controller.deploy(DEPLOYMENT.id(), versionBefore);

        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(clusterStore, never()).balance(A1_DEV_1, DEPLOYMENT.id().deploymentName());
    }

    @Test void shouldBalance() {
        givenHealthy(true);

        controller.balance(DEPLOYMENT.id());

        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(clusterStore).balance(A1_DEV_1, DEPLOYMENT.id().deploymentName());
    }

    @Test void shouldUnbalance() {
        givenHealthy(true);

        controller.unbalance(DEPLOYMENT.id());

        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(clusterStore).unbalance(A1_DEV_1, DEPLOYMENT.id().deploymentName());
    }

    @Test void shouldDeployUnbalanced() {
        givenHealthy(true);
        given(deployer.deploy(UNBALANCED_NODE, APPLICATION_NAME, versionAfter)).willReturn(audit("change", versionBefore, versionAfter));

        controller.deploy(new DeploymentId(UNBALANCED_NODE.id() + ":" + APPLICATION_NAME), versionAfter);

        verify(deployer).deploy(UNBALANCED_NODE, APPLICATION_NAME, versionAfter);
    }

    @Test void shouldUndeploy() {
        givenHealthy(true);
        givenUndeploy().willReturn(audit("remove", versionBefore, null));

        controller.undeploy(DEPLOYMENT.id());

        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(clusterStore).balance(A1_DEV_1, DEPLOYMENT.id().deploymentName());
    }

    @Test void shouldUndeployUnbalanced() {
        givenHealthy(true);
        given(deployer.undeploy(UNBALANCED_NODE, APPLICATION_NAME)).willReturn(audit("remove", versionBefore, null));

        controller.undeploy(new DeploymentId(UNBALANCED_NODE.id() + ":" + APPLICATION_NAME));

        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, UNBALANCED_NODE);
        verify(deployer).undeploy(UNBALANCED_NODE, APPLICATION_NAME);
        verify(clusterStore).balance(UNBALANCED_NODE, DEPLOYMENT.id().deploymentName());
    }

    @Test void shouldRollbackAfterDeployWithMissingAudit() {
        givenHealthy(true);
        givenDeploy().willReturn(Audits.parseYaml(""
            + "audits:\n"
            + "warning: decided to do nothing instead\n"));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT.id(), versionAfter));

        assertThat(throwable).isExactlyInstanceOf(UnexpectedAuditException.class)
            .hasMessageContaining("expected deploy audit for " + APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldRollbackAfterDeployWithWrongAuditOperation() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("remove", versionBefore, versionAfter));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT.id(), versionAfter));

        assertThat(throwable).isExactlyInstanceOf(UnexpectedAuditException.class)
            .hasMessageContaining("expected deploy audit for " + APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldRollbackAfterDeployWithoutAuditVersionChange() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", null, null));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT.id(), versionAfter));

        assertThat(throwable).isExactlyInstanceOf(UnexpectedAuditException.class)
            .hasMessageContaining("expected deploy audit for " + APPLICATION_NAME + " to change version.");
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldRollbackAfterDeployWithWrongVersionChange() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", versionBefore, VERSION_103));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT.id(), versionAfter));

        assertThat(throwable).isExactlyInstanceOf(UnexpectedAuditException.class)
            .hasMessageContaining("expected deploy audit for " + APPLICATION_NAME +
                " to change version to " + VERSION_102 + ", but changed to " + VERSION_103 + ".");
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldRollbackAfterDeployFlipsToUnhealthy() {
        givenHealthy(true, false);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT.id(), versionAfter));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage(APPLICATION_NAME + "@" + VERSION_102 + " on " + A1_DEV_1 +
                " flipped from healthy to unhealthy");
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(deployer).undeploy(A1_DEV_1, APPLICATION_NAME);
        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionBefore);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }

    @Test void shouldNotRollbackWhenDeployStaysUnhealthy() {
        givenHealthy(false);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT.id(), versionAfter);

        verify(deployer).deploy(A1_DEV_1, APPLICATION_NAME, versionAfter);
        verify(ingress).removeFromLoadBalancer(APPLICATION_NAME, A1_DEV_1);
        verify(ingress).addToLoadBalancer(APPLICATION_NAME, A1_DEV_1);
    }
}
