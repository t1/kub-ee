package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway.BadDeployerGatewayException;
import com.github.t1.kubee.model.Deployment;
import org.junit.Test;

import javax.ws.rs.NotFoundException;
import java.net.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.stream.Collectors.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class FetchDeployablesTest extends AbstractControllerTest {
    @Test
    public void shouldFetchNoDeployables() throws Exception {
        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly();
    }

    @Test
    public void shouldFetchOneDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01)).thenReturn(Stream.of(DEPLOYMENT));

        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly(DEPLOYMENT);
    }

    @Test
    public void shouldFetchTwoDeployables() throws Exception {
        Deployment foo = Deployment.builder().name("foo").node(DEV01).build();
        Deployment bar = Deployment.builder().name("bar").node(DEV01).build();

        when(controller.deployer.fetchDeployablesFrom(DEV01)).thenReturn(Stream.of(foo, bar));

        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly(foo, bar);
    }

    @Test
    public void shouldFetchErrorDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01)).thenThrow(new RuntimeException("dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchErrorCauseDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException("outer", new RuntimeException("dummy")));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchExecutionExceptionDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(ExecutionException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchConnectExceptionDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(ConnectException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchConnectRuntimeExceptionDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(
                        RuntimeException.class.getName() + ": " + ConnectException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchUnknownHostSuffixDummyDeployable() throws Exception {
        //noinspection SpellCheckingInspection
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException("dummy: nodename nor servname provided, or not known"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test
    public void shouldFetchNotFoundDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(NotFoundException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=deployer not found)");
    }

    @Test
    public void shouldFetchBadDeployerGatewayDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(BadDeployerGatewayException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=bad deployer gateway)");
    }

    @Test
    public void shouldFetchUnknownHostDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException(UnknownHostException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=unknown host)");
    }

    @Test
    public void shouldFetchConnectionRefusedDummyDummyDeployable() throws Exception {
        when(controller.deployer.fetchDeployablesFrom(DEV01))
                .thenThrow(new RuntimeException("Connection refused (Connection refused)"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=connection refused)");
    }
}
