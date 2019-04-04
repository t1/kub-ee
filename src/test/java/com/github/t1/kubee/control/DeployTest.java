package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.model.Audits;
import com.github.t1.kubee.model.DeploymentId;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.BDDMockito.BDDMyOngoingStubbing;

import static com.github.t1.kubee.model.ClusterTest.DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class DeployTest extends AbstractControllerTest {
    private static final DeploymentId DEPLOYMENT_ID = new DeploymentId(DEV01.id() + ":foo");

    private DeployerGateway deployer;
    private String versionBefore = "1.0.0";
    private String versionAfter = "1.0.2";

    @Before
    public void setup() {
        deployer = controller.deployer;
        given(deployer.fetchVersion("foo", DEV01)).will(i -> versionBefore);
    }

    private void givenHealthy(Boolean value, Boolean... values) {
        given(controller.healthGateway.fetch(DEV01, "foo")).willReturn(value, values);
    }

    private BDDMyOngoingStubbing<Audits> givenDeploy() { return givenDeploy(versionAfter); }

    private BDDMyOngoingStubbing<Audits> givenDeploy(String version) {
        return given(deployer.deploy(DEV01, "foo", version));
    }

    private BDDMyOngoingStubbing<Audits> givenUndeploy() {
        return given(deployer.undeploy(DEV01, "foo"));
    }

    private Audits audit(String operation, String oldVersion, String newVersion) {
        return Audits.parseYaml(""
            + "audits:\n"
            + "- !<deployable>\n"
            + "  operation: " + operation + "\n"
            + "  name: foo\n"
            + "  changes:\n"
            + ((oldVersion == null && newVersion == null) ? "" : "  - name: version\n")
            + ((oldVersion == null) ? "" : "    old-value: " + oldVersion + "\n")
            + ((newVersion == null) ? "" : "    new-value: " + newVersion + "\n")
            + "processState: running\n");
    }


    private void verifyDeployAndRollback() {
        verify(deployer).deploy(DEV01, "foo", versionAfter);
        verify(deployer).undeploy(DEV01, "foo");
        verify(deployer).deploy(DEV01, "foo", versionBefore);
    }

    private void verifyRemoveAndAddToLoadBalancer() {
        verifyRemoveFromLoadBalancer();
        verifyAddToLoadBalancer();
    }

    private void verifyAddToLoadBalancer() {
        assertThat(loadBalancerCalls.remove(new LoadBalancerCall("add", "foo", DEV, DEV01))).isTrue();
    }

    private void verifyRemoveFromLoadBalancer() {
        assertThat(loadBalancerCalls.remove(new LoadBalancerCall("remove", "foo", DEV, DEV01))).isTrue();
    }

    @After
    public void assertNoMore() {
        verify(deployer, atLeast(0)).fetchVersion("foo", DEV01);
        verifyNoMoreInteractions(deployer);
        assertThat(loadBalancerCalls).isEmpty();
    }


    @Test
    public void shouldDeployNew() {
        versionBefore = null;
        givenHealthy(true);
        givenDeploy().willReturn(audit("add", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT_ID, versionAfter);

        verify(deployer).deploy(DEV01, "foo", versionAfter);
        verifyAddToLoadBalancer();
    }

    @Test
    public void shouldDeployUpdate() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT_ID, versionAfter);

        verify(deployer).deploy(DEV01, "foo", versionAfter);
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldReDeploy() {
        givenHealthy(true);
        givenUndeploy().willReturn(audit("remove", versionBefore, null));
        givenDeploy(versionBefore).willReturn(audit("add", null, versionBefore));

        controller.deploy(DEPLOYMENT_ID, versionBefore);

        verify(deployer).undeploy(DEV01, "foo");
        verify(deployer).deploy(DEV01, "foo", versionBefore);
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldUnDeploy() {
        givenHealthy(true);
        givenUndeploy().willReturn(audit("remove", versionBefore, null));

        controller.undeploy(DEPLOYMENT_ID);

        verify(deployer).undeploy(DEV01, "foo");
        verifyRemoveFromLoadBalancer();
    }


    @Test
    public void shouldRollbackAfterDeployWithMissingAudit() {
        givenHealthy(true);
        givenDeploy().willReturn(Audits.parseYaml(""
            + "audits:\n"
            + "warning: decided to do nothing instead\n"));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT_ID, versionAfter));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
            .hasMessageContaining("expected deploy audit for foo");
        verifyDeployAndRollback();
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldRollbackAfterDeployWithWrongAuditOperation() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("remove", versionBefore, versionAfter));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT_ID, versionAfter));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
            .hasMessageContaining("expected deploy audit for foo");
        verifyDeployAndRollback();
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldRollbackAfterDeployWithoutAuditVersionChange() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", null, null));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT_ID, versionAfter));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
            .hasMessageContaining("expected deploy audit for foo to change version.");
        verifyDeployAndRollback();
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldRollbackAfterDeployWithWrongVersionChange() {
        givenHealthy(true);
        givenDeploy().willReturn(audit("change", versionBefore, "1.0.3"));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT_ID, versionAfter));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
            .hasMessageContaining("expected deploy audit for foo to change version to 1.0.2, but changed to 1.0.3.");
        verifyDeployAndRollback();
        verifyRemoveAndAddToLoadBalancer();
    }


    @Test
    public void shouldRollbackAfterDeployFlipsToUnhealthy() {
        givenHealthy(true, false);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        Throwable throwable = catchThrowable(() -> controller.deploy(DEPLOYMENT_ID, versionAfter));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("foo@1.0.2 on server-a:1:DEV:1 flipped from healthy to unhealthy");
        verifyDeployAndRollback();
        verifyRemoveAndAddToLoadBalancer();
    }

    @Test
    public void shouldNotRollbackWhenDeployStaysUnhealthy() {
        givenHealthy(false);
        givenDeploy().willReturn(audit("change", versionBefore, versionAfter));

        controller.deploy(DEPLOYMENT_ID, versionAfter);

        verify(deployer).deploy(DEV01, "foo", versionAfter);
        verifyRemoveAndAddToLoadBalancer();
    }
}
