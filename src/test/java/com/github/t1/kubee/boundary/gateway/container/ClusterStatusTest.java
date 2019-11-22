package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.TestData;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.tools.ContainersFixture;
import com.github.t1.kubee.tools.ContainersFixture.Container;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.ALL_ENDPOINTS;
import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.LOCAL;
import static com.github.t1.kubee.TestData.LOCAL_ENDPOINT;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT1;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT2;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT3;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT4;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT5;
import static com.github.t1.kubee.TestData.QA_ENDPOINT1;
import static com.github.t1.kubee.TestData.QA_ENDPOINT2;
import static com.github.t1.kubee.TestData.SLOT_0;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClusterStatusTest {
    @RegisterExtension ContainersFixture containers = new ContainersFixture();

    private ClusterStatus whenStatus() {
        return new ClusterStatus(CLUSTER, containers.getDockerComposeDir());
    }

    @Test void shouldGetNoEndpoint() {
        containers.given();

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).isEmpty();
    }

    @Test void shouldGetOneEndpoint() {
        containers.given(PROD_ENDPOINT1);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(PROD_ENDPOINT1);
    }

    @Test void shouldGetThreeEndpoints() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldGetPort2() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);

        Integer port = whenStatus().port(PROD_ENDPOINT2.getHost());

        assertThat(port).isEqualTo(PROD_ENDPOINT2.getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        containers.given(PROD_ENDPOINT1);

        Integer port = whenStatus().port(PROD_ENDPOINT2.getHost());

        assertThat(port).isNull();
    }

    @Test void shouldNotScaleThree() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldScaleOneUp() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldScaleTwoUp() {
        containers.given(PROD_ENDPOINT1);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldScaleOneDown() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3, PROD_ENDPOINT4);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldScaleTwoDown() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3, PROD_ENDPOINT4, PROD_ENDPOINT5);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldScaleOneOfTwoStages() {
        containers.given(QA_ENDPOINT1, PROD_ENDPOINT1, PROD_ENDPOINT2);

        List<Endpoint> scaledEndpoints = whenStatus().scale(TestData.PROD);

        containers.verifyScaled(TestData.PROD, 3);
        assertThat(scaledEndpoints).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);
    }

    @Test void shouldNotScaleOne() {
        containers.given(LOCAL_ENDPOINT);

        List<Endpoint> scaledEndpoints = whenStatus().scale(LOCAL);

        containers.verifyScaled(LOCAL, 1);
        assertThat(scaledEndpoints).containsExactly(LOCAL_ENDPOINT);
    }

    @Test void shouldScaleZeroToOne() {
        containers.given();

        List<Endpoint> scaledEndpoints = whenStatus().scale(LOCAL);

        containers.verifyScaled(LOCAL, 1);
        assertThat(scaledEndpoints).containsExactly(LOCAL_ENDPOINT);
    }

    @Test void shouldFailToScaleWhenDockerComposeScaleFails() {
        containers.given();
        containers.givenScaleResult("local-worker", 1, "ERROR: Number of containers for service \"local-worker\" is not a number");

        Throwable throwable = catchThrowable(() -> whenStatus().scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("'docker-compose up --detach --scale local-worker=1' returned error 1:\n"
                + "ERROR: Number of containers for service \"local-worker\" is not a number");
        containers.verifyScaled(LOCAL, 0);
    }

    @Test void shouldGetOneQaEndpoint() {
        containers.given(QA_ENDPOINT1);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(QA_ENDPOINT1);
    }

    @Test void shouldGetLocalAndQaEndpoints() {
        containers.given(LOCAL_ENDPOINT, QA_ENDPOINT1, QA_ENDPOINT2);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactly(LOCAL_ENDPOINT, QA_ENDPOINT1, QA_ENDPOINT2);
    }

    @Test void shouldGetAllEndpoints() {
        containers.given(ALL_ENDPOINTS);

        Stream<Endpoint> endpoints = whenStatus().endpoints();

        assertThat(endpoints).containsExactlyElementsOf(ALL_ENDPOINTS);
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsFails() {
        Container container = containers.given(LOCAL_ENDPOINT);
        container.dockerInfo(1, "template: :1: unclosed action");

        Throwable throwable = catchThrowable(() -> whenStatus().scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("'docker ps --all --format {{.Ports}}\t{{.Names}}" +
                " --filter id=" + container.getId()
                + " --filter publish=" + SLOT_0.getHttp() + "' returned error 1:\n" +
                "template: :1: unclosed action");
    }

    @Test void shouldFailToReadClusterStatusWhenDockerPsNotFound() {
        Container container = containers.given(LOCAL_ENDPOINT);
        container.dockerInfo(0, "");

        Throwable throwable = catchThrowable(() -> whenStatus().scale(LOCAL));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("no docker status for container " + container.getId());
    }
}
