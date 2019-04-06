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
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class StatusTest {
    private static final Slot SLOT = Slot.builder().build();
    private static final Stage STAGE = Stage.builder().name("PROD").count(3).build();
    private static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).stage(STAGE).build();

    private static final Endpoint WORKER01 = new Endpoint("worker1", 32769);
    private static final Endpoint WORKER02 = new Endpoint("worker2", 32770);
    private static final Endpoint WORKER03 = new Endpoint("worker3", 32771);

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
    }

    private Status whenStatus() {
        return new Status(System.out::println, CLUSTER, dockerComposeConfigPath);
    }

    @Test void shouldGetOneEndpoint() {
        givenContainers(WORKER01);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER01);
    }

    @Test void shouldGetThreeEndpoints() {
        givenContainers(WORKER01, WORKER02, WORKER03);

        List<Endpoint> endpoints = whenStatus().endpoints().collect(toList());

        assertThat(endpoints).containsExactly(WORKER01, WORKER02, WORKER03);
    }

    @Test void shouldGetPort2() {
        givenContainers(WORKER01, WORKER02, WORKER03);

        Integer port = whenStatus().port(WORKER02.getHost());

        assertThat(port).isEqualTo(WORKER02.getPort());
    }

    @Test void shouldGetNullPortForUnknownHost() {
        givenContainers(WORKER01);

        Integer port = whenStatus().port(WORKER02.getHost());

        assertThat(port).isNull();
    }
}
