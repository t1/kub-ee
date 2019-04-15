package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.tools.http.YamlHttpClient.BadGatewayException;
import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class FetchDeployablesTest extends AbstractControllerTest {
    @Test void shouldFetchNoDeployables() {
        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly();
    }

    @Test void shouldFetchOneDeployable() {
        when(controller.deployer.fetchDeployables(DEV01)).thenReturn(Stream.of(DEPLOYMENT));

        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly(DEPLOYMENT);
    }

    @Test void shouldFetchTwoDeployables() {
        Deployment foo = Deployment.builder().name("foo").node(DEV01).build();
        Deployment bar = Deployment.builder().name("bar").node(DEV01).build();

        when(controller.deployer.fetchDeployables(DEV01)).thenReturn(Stream.of(foo, bar));

        Stream<Deployment> deployments = controller.fetchDeploymentsOn(DEV01);

        assertThat(deployments).containsExactly(foo, bar);
    }

    @Test void shouldFetchErrorDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01)).thenThrow(new RuntimeException("dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchErrorCauseDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException("outer", new RuntimeException("dummy")));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchExecutionExceptionDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(ExecutionException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchConnectExceptionDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(ConnectException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchConnectRuntimeExceptionDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(
                RuntimeException.class.getName() + ": " + ConnectException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchUnknownHostSuffixDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException("dummy: nodename nor servname provided, or not known"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=dummy)");
    }

    @Test void shouldFetchNotFoundDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(NotFoundException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=deployer not found)");
    }

    @Test void shouldFetchBadDeployerGatewayDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(BadGatewayException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=bad deployer gateway)");
    }

    @Test void shouldFetchUnknownHostDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException(UnknownHostException.class.getName() + ": dummy"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=unknown host)");
    }

    @Test void shouldFetchConnectionRefusedDummyDummyDeployable() {
        when(controller.deployer.fetchDeployables(DEV01))
            .thenThrow(new RuntimeException("Connection refused (Connection refused)"));

        List<Deployment> deployments = controller.fetchDeploymentsOn(DEV01).collect(toList());

        assertThat(deployments).hasSize(1);
        assertThat(deployments.get(0)).hasToString("Deployment(-:-|-:-:-|" + DEV01 + "|error=connection refused)");
    }
}
