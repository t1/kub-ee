package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.tools.ContainersFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.ContainersFixture.ALL_WORKERS;
import static com.github.t1.kubee.tools.ContainersFixture.CLUSTER;
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
}
