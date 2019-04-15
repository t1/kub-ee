package com.github.t1.kubee.tools;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Accessors(chain = true)
public class ContainersFixture implements BeforeEachCallback, AfterEachCallback {
    public static final Slot SLOT = Slot.builder().build();
    public static final Stage STAGE = Stage.builder().name("PROD").count(3).build();
    public static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).stage(STAGE).build();

    public static final Endpoint WORKER1 = new Endpoint("worker1", 32769);
    public static final Endpoint WORKER2 = new Endpoint("worker2", 32770);
    public static final Endpoint WORKER3 = new Endpoint("worker3", 32771);
    public static final Endpoint WORKER4 = new Endpoint("worker4", 32772);
    public static final Endpoint WORKER5 = new Endpoint("worker5", 32773);
    private static final List<Endpoint> WORKERS = asList(WORKER1, WORKER2, WORKER3, WORKER4, WORKER5);

    private final ProcessInvoker originalProcessInvoker = ProcessInvoker.INSTANCE;
    private final ProcessInvoker proc = mock(ProcessInvoker.class);
    @Setter @Getter private Path dockerComposeDir = Paths.get("src/test/docker/");
    @Setter private int port = 80;

    @Override public void afterEach(ExtensionContext context) { ProcessInvoker.INSTANCE = originalProcessInvoker; }

    @Override public void beforeEach(ExtensionContext context) { ProcessInvoker.INSTANCE = proc; }

    public void givenEndpoints(Endpoint... workers) {
        List<String> containerIds = new ArrayList<>();
        for (int i = 0; i < workers.length; i++) {
            String containerId = UUID.randomUUID().toString();
            containerIds.add(containerId);
            given(proc.invoke("docker", "ps", "--all", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + port))
                .willReturn("0.0.0.0:" + workers[i].getPort() + "->" + port + "/tcp\tdocker_worker_" + (i + 1));
        }
        given(proc.invoke(dockerComposeDir, "docker-compose", "ps", "-q", "worker"))
            .willReturn(join("\n", containerIds));
        given(proc.invoke(eq(dockerComposeDir), eq("docker-compose"), eq("up"), eq("--detach"), eq("--scale"), anyString()))
            .will(i -> {
                String scaleExpression = i.getArgument(5);
                assertThat(scaleExpression).startsWith("worker=");
                int scale = Integer.parseInt(scaleExpression.substring(7));
                givenEndpoints(WORKERS.subList(0, scale).toArray(new Endpoint[0]));
                return "dummy-scale-output";
            });
    }
}