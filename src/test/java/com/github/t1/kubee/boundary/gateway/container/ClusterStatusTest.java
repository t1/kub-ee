package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.tools.ContainersFixture;
import com.github.t1.kubee.tools.cli.Script.Result;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.ContainersFixture.ALL_WORKERS;
import static com.github.t1.kubee.tools.ContainersFixture.CLUSTER;
import static com.github.t1.kubee.tools.ContainersFixture.LOCAL;
import static com.github.t1.kubee.tools.ContainersFixture.LOCAL_WORKER;
import static com.github.t1.kubee.tools.ContainersFixture.PROD;
import static com.github.t1.kubee.tools.ContainersFixture.PROD_WORKER1;
import static com.github.t1.kubee.tools.ContainersFixture.PROD_WORKER2;
import static com.github.t1.kubee.tools.ContainersFixture.PROD_WORKER3;
import static com.github.t1.kubee.tools.ContainersFixture.PROD_WORKER4;
import static com.github.t1.kubee.tools.ContainersFixture.PROD_WORKER5;
import static com.github.t1.kubee.tools.ContainersFixture.QA_WORKER1;
import static com.github.t1.kubee.tools.ContainersFixture.QA_WORKER2;
import static com.github.t1.kubee.tools.ContainersFixture.SLOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClusterStatusTest {
    @RegisterExtension ContainersFixture containers = new ContainersFixture().setPort(SLOT.getHttp());

    private ClusterStatus whenStatus() {
        return new ClusterStatus(CLUSTER, containers.getDockerComposeDir());
    }

    @Test void shouldGetNoEndpoint() {
        containers.givenEndpoints();

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).isEmpty();
    }

    @Test void shouldGetOneEndpoint() {
        containers.givenEndpoints(PROD_WORKER1);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(PROD_WORKER1);
    }

    @Test void shouldGetThreeEndpoints() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldGetPort2() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);

        Integer port = whenStatus().port(PROD_WORKER2.getHost());

        assertThat(port).isEqualTo(PROD_WORKER2.getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        containers.givenEndpoints(PROD_WORKER1);

        Integer port = whenStatus().port(PROD_WORKER2.getHost());

        assertThat(port).isNull();
    }

    @Test void shouldNotScale() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);

        List<Endpoint> scaledEndpoints = whenStatus().scale(PROD);

        assertThat(scaledEndpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldScaleOneUp() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2);

        List<Endpoint> scaledEndpoints = whenStatus().scale(PROD);

        assertThat(scaledEndpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldScaleTwoUp() {
        containers.givenEndpoints(PROD_WORKER1);

        List<Endpoint> scaledEndpoints = whenStatus().scale(PROD);

        assertThat(scaledEndpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldScaleOneDown() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3, PROD_WORKER4);

        List<Endpoint> scaledEndpoints = whenStatus().scale(PROD);

        assertThat(scaledEndpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldScaleTwoDown() {
        containers.givenEndpoints(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3, PROD_WORKER4, PROD_WORKER5);

        List<Endpoint> scaledEndpoints = whenStatus().scale(PROD);

        assertThat(scaledEndpoints).containsExactly(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);
    }

    @Test void shouldGetOneQaEndpoint() {
        containers.givenEndpoints(QA_WORKER1);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(QA_WORKER1);
    }

    @Test void shouldGetLocalAndQaEndpoints() {
        containers.givenEndpoints(LOCAL_WORKER, QA_WORKER1, QA_WORKER2);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(LOCAL_WORKER, QA_WORKER1, QA_WORKER2);
    }

    @Test void shouldGetAllEndpoints() {
        containers.givenEndpoints(ALL_WORKERS);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactlyElementsOf(ALL_WORKERS);
    }

    @Test void shouldFailToReadClusterStatusWhenDockerComposeFails() {
        containers.givenDockerCompose("ps -q " + LOCAL.serviceName(CLUSTER))
            .willReturn(new Result(1, "ERROR: No such service: local-worker"));

        Throwable throwable = catchThrowable(this::whenStatus);

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("docker-compose ps -q local-worker returned error 1:\n" +
                "ERROR: No such service: local-worker");
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsFails() {
        String containerId = UUID.randomUUID().toString();
        containers.givenDockerCompose("ps -q " + LOCAL.serviceName(CLUSTER))
            .willReturn(new Result(0, containerId));
        containers.givenDocker("ps --all --format \"{{.Ports}}\t{{.Names}}\" "
            + "--filter id=" + containerId + " --filter publish=" + SLOT.getHttp())
            .willReturn(new Result(1, "template: :1: unclosed action"));

        Throwable throwable = catchThrowable(this::whenStatus);

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("docker ps --all --format \"{{.Ports}}\t{{.Names}}\" --filter id=" + containerId + " --filter publish=80 returned error 1:\n" +
                "template: :1: unclosed action");
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsNotFound() {
        String containerId = UUID.randomUUID().toString();
        containers.givenDockerCompose("ps -q " + LOCAL.serviceName(CLUSTER))
            .willReturn(new Result(0, containerId));
        containers.givenDocker("ps --all --format \"{{.Ports}}\t{{.Names}}\" "
            + "--filter id=" + containerId + " --filter publish=" + SLOT.getHttp())
            .willReturn(new Result(0, ""));

        Throwable throwable = catchThrowable(this::whenStatus);

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("no docker status for container " + containerId);
    }

    @Test void shouldFailToScaleClusterWhenDockerComposeScaleFails() {
        containers.givenEndpoints(PROD_WORKER1);
        containers.givenDockerCompose("up --detach --scale worker=3")
            .willReturn(new Result(1, "ERROR: Number of containers for service \"worker\" is not a number"));

        Throwable throwable = catchThrowable(this::whenStatus);

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("docker-compose ps -q worker returned error 1:\n" +
                "ERROR: Number of containers for service \"worker\" is not a number");
    }
}
