package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.tools.ContainersFixture;
import com.github.t1.kubee.tools.ContainersFixture.Container;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.LOCAL;
import static com.github.t1.kubee.TestData.LOCAL1;
import static com.github.t1.kubee.TestData.PROD;
import static com.github.t1.kubee.TestData.PROD01;
import static com.github.t1.kubee.TestData.PROD02;
import static com.github.t1.kubee.TestData.PROD03;
import static com.github.t1.kubee.TestData.PROD04;
import static com.github.t1.kubee.TestData.PROD05;
import static com.github.t1.kubee.TestData.QA;
import static com.github.t1.kubee.TestData.QA1;
import static com.github.t1.kubee.TestData.QA2;
import static com.github.t1.kubee.TestData.SLOT_0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClusterStatusTest {
    @RegisterExtension ContainersFixture containers = new ContainersFixture();

    private final ClusterStatus status = new ClusterStatus(CLUSTER, containers.getDockerComposeDir());

    @Test void shouldGetNoEndpoint() {
        containers.given();

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints).isEmpty();
    }

    @Test void shouldGetOneEndpoint() {
        containers.given(PROD01);

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints).containsExactly(containers.thoseEndpointsIn(PROD, 1));
    }

    @Test void shouldGetThreeEndpoints() {
        containers.given(PROD01, PROD02, PROD03);

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints).containsExactly(containers.thoseEndpointsIn(PROD, 3));
    }

    @Test void shouldGetPort2() {
        containers.given(PROD01, PROD02, PROD03);

        Integer port = status.exposedPort(PROD02.host());

        assertThat(port).isEqualTo(containers.endpointsIn(PROD).get(1).getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        containers.given(PROD01);

        Integer port = status.exposedPort(PROD02.host());

        assertThat(port).isNull();
    }

    @Test void shouldNotScaleThree() {
        containers.given(PROD01, PROD02, PROD03);

        status.scale(PROD);

        containers.verifyScaled(PROD, 3);
    }

    @Test void shouldScaleOneUp() {
        containers.given(PROD01, PROD02);

        status.scale(PROD);

        containers.verifyScaled(PROD, 3);
    }

    @Test void shouldScaleTwoUp() {
        containers.given(PROD01);

        status.scale(PROD);

        containers.verifyScaled(PROD, 3);
    }

    @Test void shouldScaleOneDown() {
        containers.given(PROD01, PROD02, PROD03, PROD04);

        status.scale(PROD);

        containers.verifyScaled(PROD, 3);
    }

    @Test void shouldScaleTwoDown() {
        containers.given(PROD01, PROD02, PROD03, PROD04, PROD05);

        status.scale(PROD);

        containers.verifyScaled(PROD, 3);
    }

    @Test void shouldScaleOneOfTwoStages() {
        containers.given(QA1, PROD01, PROD02);

        status.scale(PROD);

        containers.verifyScaled(QA, 1); // as is
        containers.verifyScaled(PROD, 3); // one up
    }

    @Test void shouldNotScaleOne() {
        containers.given(LOCAL1);

        status.scale(LOCAL);

        containers.verifyScaled(LOCAL, 1);
    }

    @Test void shouldScaleZeroToOne() {
        containers.given();

        status.scale(LOCAL);

        containers.verifyScaled(LOCAL, 1);
    }

    @Test void shouldFailToScaleWhenDockerComposeScaleFails() {
        containers.given();
        containers.givenScaleResult("local-worker", 1, "ERROR: Number of containers for service \"local-worker\" is not a number");

        Throwable throwable = catchThrowable(() -> status.scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("'docker-compose up --no-color --quiet-pull --detach --scale local-worker=1' returned 1:\n"
                + "ERROR: Number of containers for service \"local-worker\" is not a number");
        containers.verifyScaled(LOCAL, 0);
    }

    @Test void shouldGetOneQaEndpoint() {
        containers.given(QA1);

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints).containsExactly(containers.thoseEndpointsIn(QA, 1));
    }

    @Test void shouldGetLocalAndQaEndpoints() {
        containers.given(LOCAL1, QA1, QA2);

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints)
            .hasSize(3)
            .contains(containers.thoseEndpointsIn(LOCAL, 1))
            .contains(containers.thoseEndpointsIn(QA, 2));
    }

    @Test void shouldGetAllEndpoints() {
        containers.given(LOCAL1, QA1, QA2, PROD01, PROD02, PROD03);

        Stream<Endpoint> endpoints = status.endpoints();

        assertThat(endpoints)
            .hasSize(6)
            .contains(containers.thoseEndpointsIn(LOCAL, 1))
            .contains(containers.thoseEndpointsIn(QA, 2))
            .contains(containers.thoseEndpointsIn(PROD, 3));
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsFails() {
        Container container = containers.given(LOCAL1);
        container.dockerInfo(1, "template: :1: unclosed action");

        Throwable throwable = catchThrowable(() -> status.scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("'docker ps --all --format {{.Ports}}\t{{.Names}}" +
                " --filter id=" + container.getId()
                + " --filter publish=" + SLOT_0.getHttp() + "' returned 1:\n" +
                "template: :1: unclosed action");
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsNotFound() {
        Container container = containers.given(LOCAL1);
        container.dockerInfo(0, "");

        Throwable throwable = catchThrowable(() -> status.scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("no docker status for container " + container.getId());
    }
}
