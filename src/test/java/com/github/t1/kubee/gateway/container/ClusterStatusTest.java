package com.github.t1.kubee.gateway.container;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.Slot;
import com.github.t1.kubee.model.Stage;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClusterStatusTest {
    private static final Slot SLOT = Slot.builder().build();
    private static final Stage STAGE = Stage.builder().name("PROD").count(3).build();
    private static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).stage(STAGE).build();

    private static final Endpoint WORKER1 = new Endpoint("worker1", 32769);
    private static final Endpoint WORKER2 = new Endpoint("worker2", 32770);
    private static final Endpoint WORKER3 = new Endpoint("worker3", 32771);
    private static final Endpoint WORKER4 = new Endpoint("worker3", 32772);
    private static final Endpoint WORKER5 = new Endpoint("worker3", 32773);
    private static final List<Endpoint> WORKERS = asList(WORKER1, WORKER2, WORKER3, WORKER4, WORKER5);

    private final Path dockerComposeConfigPath = Paths.get("src/test/docker/docker-compose.yaml");

    private final ProcessInvoker originalProcessInvoker = ProcessInvoker.INSTANCE;
    private final ProcessInvoker proc = mock(ProcessInvoker.class);

    @BeforeEach void setUpProcessInvoker() { ProcessInvoker.INSTANCE = proc; }

    @AfterEach void tearDownProcessInvoker() { ProcessInvoker.INSTANCE = originalProcessInvoker; }

    private void givenContainers(Endpoint... workers) {
        List<String> containerIds = new ArrayList<>();
        for (int i = 0; i < workers.length; i++) {
            String containerId = UUID.randomUUID().toString();
            containerIds.add(containerId);
            given(proc.invoke("docker", "ps", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + SLOT.getHttp()))
                .willReturn("0.0.0.0:" + workers[i].getPort() + "->" + SLOT.getHttp() + "/tcp\tdocker_worker_" + (i + 1));
        }
        given(proc.invoke(dockerComposeConfigPath.getParent(), "docker-compose", "ps", "-q", "worker"))
            .willReturn(join("\n", containerIds));
        given(proc.invoke(eq(dockerComposeConfigPath), eq("docker-compose"), eq("up"), eq("--detach"), eq("--scale"), anyString()))
            .will(i -> {
                String scaleExpression = i.getArgument(5); // "worker=3"
                assertThat(scaleExpression).startsWith("worker=");
                int scale = Integer.parseInt(scaleExpression.substring(7));
                givenContainers(WORKERS.subList(0, scale).toArray(new Endpoint[0]));
                return "dummy-scale-output";
            });
    }

    private ClusterStatus whenStatus() {
        return new ClusterStatus(System.out::println, CLUSTER, dockerComposeConfigPath);
    }

    @Test void shouldGetOneEndpoint() {
        givenContainers(WORKER1);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER1);
    }

    @Test void shouldGetThreeEndpoints() {
        givenContainers(WORKER1, WORKER2, WORKER3);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldGetPort2() {
        givenContainers(WORKER1, WORKER2, WORKER3);

        Integer port = whenStatus().port(WORKER2.getHost());

        assertThat(port).isEqualTo(WORKER2.getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        givenContainers(WORKER1);

        Integer port = whenStatus().port(WORKER2.getHost());

        assertThat(port).isNull();
    }

    @Test void shouldNotScale() {
        givenContainers(WORKER1, WORKER2, WORKER3);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleOneUp() {
        givenContainers(WORKER1, WORKER2);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleTwoUp() {
        givenContainers(WORKER1);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleOneDown() {
        givenContainers(WORKER1, WORKER2, WORKER3, WORKER4);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }

    @Test void shouldScaleTwoDown() {
        givenContainers(WORKER1, WORKER2, WORKER3, WORKER4, WORKER5);

        List<Endpoint> scaledEndpoints = whenStatus().scale(STAGE);

        assertThat(scaledEndpoints).containsExactly(WORKER1, WORKER2, WORKER3);
    }
}
