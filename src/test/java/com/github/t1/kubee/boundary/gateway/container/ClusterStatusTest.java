package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.ContainersFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static com.github.t1.kubee.tools.ContainersFixture.WORKER1;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER2;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER3;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER4;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER5;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterStatusTest {
    private static final Slot SLOT = Slot.builder().build();
    private static final Stage STAGE = Stage.builder().name("PROD").count(3).build();
    private static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).stage(STAGE).build();

    @RegisterExtension ContainersFixture containers = new ContainersFixture().setPort(SLOT.getHttp());

    private ClusterStatus whenStatus() {
        return new ClusterStatus(CLUSTER, containers.getDockerComposeDir());
    }

    @Test void shouldGetNoEndpoint() {
        containers.givenEndpoints();

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).isEmpty();
    }

    @Test void shouldGetOneEndpoint() {
        containers.givenEndpoints(WORKER1);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER1);
    }

    @Test void shouldGetThreeEndpoints() {
        containers.givenEndpoints(WORKER1, WORKER2, WORKER3);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldGetPort2() {
        containers.givenEndpoints(WORKER1, WORKER2, WORKER3);

        Integer port = whenStatus().port(WORKER2.getHost());

        assertThat(port).isEqualTo(WORKER2.getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        containers.givenEndpoints(WORKER1);

        Integer port = whenStatus().port(WORKER2.getHost());

        assertThat(port).isNull();
    }

    @Test void shouldNotScale() {
        containers.givenEndpoints(WORKER1, WORKER2, WORKER3);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleOneUp() {
        containers.givenEndpoints(WORKER1, WORKER2);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleTwoUp() {
        containers.givenEndpoints(WORKER1);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleOneDown() {
        containers.givenEndpoints(WORKER1, WORKER2, WORKER3, WORKER4);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleTwoDown() {
        containers.givenEndpoints(WORKER1, WORKER2, WORKER3, WORKER4, WORKER5);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }
}
