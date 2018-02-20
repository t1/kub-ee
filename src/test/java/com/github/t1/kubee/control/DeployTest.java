package com.github.t1.kubee.control;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import org.junit.*;
import org.mockito.stubbing.OngoingStubbing;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.ThrowableAssert.catchThrowable;
import static org.mockito.Mockito.*;

public class DeployTest extends AbstractControllerTest {
    @Before
    public void givenVersion100() {
        when(controller.deployer.fetchVersion("foo", DEV01)).thenReturn("1.0.0");
    }

    private void givenHealthy(Boolean value, Boolean... values) {
        when(controller.healthGateway.fetch(DEV01, "foo")).thenReturn(value, values);
    }

    private OngoingStubbing<Audits> whenDeploying() { return whenDeploying("1.0.2"); }

    private OngoingStubbing<Audits> whenDeploying(String version) {
        return when(controller.deployer.deploy(DEV01, "foo", version));
    }

    private OngoingStubbing<Audits> whenUndeploying() {
        return when(controller.deployer.undeploy(DEV01, "foo"));
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
        verify(controller.deployer).deploy(DEV01, "foo", "1.0.2");
        verify(controller.deployer).undeploy(DEV01, "foo");
        verify(controller.deployer).deploy(DEV01, "foo", "1.0.0");
    }

    @After
    public void assertNoMoreDeployments() {
        verify(controller.deployer, atLeast(0)).fetchVersion("foo", DEV01);
        verifyNoMoreInteractions(controller.deployer);
    }


    @Test
    public void shouldDeploy() {
        givenHealthy(true);
        whenDeploying().thenReturn(audit("change", "1.0.0", "1.0.2"));

        controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2");

        verify(controller.deployer).deploy(DEV01, "foo", "1.0.2");
    }

    @Test
    public void shouldReDeploy() {
        givenHealthy(true);
        whenUndeploying().thenReturn(audit("remove", "1.0.0", null));
        whenDeploying("1.0.0").thenReturn(audit("add", null, "1.0.0"));

        controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.0");

        verify(controller.deployer).undeploy(DEV01, "foo");
        verify(controller.deployer).deploy(DEV01, "foo", "1.0.0");
    }

    @Test
    public void shouldUnDeploy() {
        givenHealthy(true);
        whenUndeploying().thenReturn(audit("remove", "1.0.0", null));

        controller.undeploy(new DeploymentId(DEV01.id() + ":foo"));

        verify(controller.deployer).undeploy(DEV01, "foo");
    }


    @Test
    public void shouldRollbackAfterDeployWithMissingAudit() {
        givenHealthy(true);
        whenDeploying().thenReturn(Audits.parseYaml(""
                + "audits:\n"
                + "warning: decided to do nothing instead\n"));

        Throwable throwable = catchThrowable(() -> controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2"));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
                             .hasMessageContaining("expected deploy audit for foo");
        verifyDeployAndRollback();
    }

    @Test
    public void shouldRollbackAfterDeployWithWrongAuditOperation() {
        givenHealthy(true);
        whenDeploying().thenReturn(audit("remove", "1.0.0", "1.0.2"));

        Throwable throwable = catchThrowable(() -> controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2"));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
                             .hasMessageContaining("expected deploy audit for foo");
        verifyDeployAndRollback();
    }

    @Test
    public void shouldRollbackAfterDeployWithoutAuditVersionChange() {
        givenHealthy(true);
        whenDeploying().thenReturn(audit("change", null,null));

        Throwable throwable = catchThrowable(() -> controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2"));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
                             .hasMessageContaining("expected deploy audit for foo to change version.");
        verifyDeployAndRollback();
    }

    @Test
    public void shouldRollbackAfterDeployWithWrongVersionChange() {
        givenHealthy(true);
        whenDeploying().thenReturn(audit("change", "1.0.0","1.0.3"));

        Throwable throwable = catchThrowable(() -> controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2"));

        assertThat(throwable).isExactlyInstanceOf(WebApplicationApplicationException.class)
                             .hasMessageContaining("expected deploy audit for foo to change version to 1.0.2, but changed to 1.0.3.");
        verifyDeployAndRollback();
    }


    @Test
    public void shouldRollbackAfterDeployFlipsToUnhealthy() {
        givenHealthy(true, false);
        whenDeploying().thenReturn(audit("change", "1.0.0", "1.0.2"));

        Throwable throwable = catchThrowable(() -> controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2"));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
                             .hasMessage("foo@1.0.2 on server-a:1:DEV:1 flipped from healthy to unhealthy");
        verifyDeployAndRollback();
    }

    @Test
    public void shouldNotRollbackWhenDeployStaysUnhealthy() {
        givenHealthy(false);
        whenDeploying().thenReturn(audit("change", "1.0.0", "1.0.2"));

        controller.deploy(new DeploymentId(DEV01.id() + ":foo"), "1.0.2");

        verify(controller.deployer).deploy(DEV01, "foo", "1.0.2");
    }
}
