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
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@Accessors(chain = true)
public class ContainersFixture implements BeforeEachCallback, AfterEachCallback, Extension {
    public static final Slot SLOT = Slot.builder().build();
    public static final Stage LOCAL = Stage.builder().name("LOCAL").prefix("local-").count(1).build();
    public static final Stage QA = Stage.builder().name("QA").suffix("-qa").count(2).build();
    public static final Stage PROD = Stage.builder().name("PROD").count(3).build();
    public static final Cluster CLUSTER = Cluster.builder()
        .host("worker")
        .slot(SLOT)
        .stage(LOCAL).stage(QA).stage(PROD)
        .build();

    public static final Endpoint LOCAL_WORKER = new Endpoint("local-worker", 8080);
    public static final Endpoint QA_WORKER1 = new Endpoint("worker-qa1", 8080);
    public static final Endpoint QA_WORKER2 = new Endpoint("worker-qa2", 8080);
    public static final Endpoint PROD_WORKER1 = new Endpoint("worker1", 8080);
    public static final Endpoint PROD_WORKER2 = new Endpoint("worker2", 8080);
    public static final Endpoint PROD_WORKER3 = new Endpoint("worker3", 8080);
    public static final Endpoint PROD_WORKER4 = new Endpoint("worker4", 8080);
    public static final Endpoint PROD_WORKER5 = new Endpoint("worker5", 8080);
    private static final List<Endpoint> LOCAL_WORKERS = singletonList(LOCAL_WORKER);
    private static final List<Endpoint> QA_WORKERS = asList(QA_WORKER1, QA_WORKER2);
    private static final List<Endpoint> PROD_WORKERS = asList(PROD_WORKER1, PROD_WORKER2, PROD_WORKER3, PROD_WORKER4, PROD_WORKER5);
    public static final List<Endpoint> ALL_WORKERS = asList(LOCAL_WORKER, QA_WORKER1, QA_WORKER2,
        PROD_WORKER1, PROD_WORKER2, PROD_WORKER3);

    private final ProcessInvoker originalProcessInvoker = ProcessInvoker.INSTANCE;
    private final ProcessInvoker proc = mock(ProcessInvoker.class);
    @Setter @Getter private Path dockerComposeDir = Paths.get("src/test/docker/");
    @Setter private int port = 80;

    @Override public void afterEach(ExtensionContext context) { ProcessInvoker.INSTANCE = originalProcessInvoker; }

    @Override public void beforeEach(ExtensionContext context) { ProcessInvoker.INSTANCE = proc; }

    public void givenEndpoints(Endpoint... workers) { givenEndpoints(asList(workers)); }

    public void givenEndpoints(List<Endpoint> endpoints) {
        Map<Stage, List<String>> containerIds = givenContainers(endpoints);
        CLUSTER.stages().forEach(stage -> givenProcessIdsInvocation(containerIds, stage));
        givenScaleInvocation();
    }

    private Map<Stage, List<String>> givenContainers(List<Endpoint> endpoints) {
        Map<Stage, List<String>> containerIds = new LinkedHashMap<>();
        for (Endpoint endpoint : endpoints) {
            String containerId = UUID.randomUUID().toString();
            Stage stage = stageOf(endpoint);
            List<String> ids = containerIds.computeIfAbsent(stage, s -> new ArrayList<>());
            ids.add(containerId);
            int i = ids.size(); // the actual index of the added container
            String name = stage.serviceName(CLUSTER);
            given(proc.invoke("docker", "ps", "--all", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + port))
                .willReturn("0.0.0.0:" + endpoint.getPort() + "->" + port + "/tcp\tdocker_" + name + "_" + i);
        }
        return containerIds;
    }

    private void givenProcessIdsInvocation(Map<Stage, List<String>> containerIds, Stage stage) {
        List<String> ids = containerIds.get(stage);
        given(proc.invoke(dockerComposeDir, "docker-compose", "ps", "-q", stage.serviceName(CLUSTER)))
            .willReturn((ids == null) ? "" : join("\n", ids));
    }

    /** when scaling, the running endpoints are re-stubbed */
    private void givenScaleInvocation() {
        given(proc.invoke(eq(dockerComposeDir), eq("docker-compose"), eq("up"), eq("--detach"), eq("--scale"), anyString()))
            .will(i -> {
                String scaleExpression = i.getArgument(5);
                String[] scaleSplit = scaleExpression.split("=");
                List<Endpoint> endpoints = endpointsFor(scaleSplit[0]);
                int scaleValue = Integer.parseInt(scaleExpression.substring(7));
                givenEndpoints(endpoints.subList(0, scaleValue).toArray(new Endpoint[0]));
                return "dummy-scale-output";
            });
    }

    private List<Endpoint> endpointsFor(String name) {
        switch (name) {
            case "local-worker":
                return LOCAL_WORKERS;
            case "worker-qa":
                return QA_WORKERS;
            case "worker":
                return PROD_WORKERS;
        }
        throw new IllegalArgumentException("unknown stage " + name);
    }

    private Stage stageOf(Endpoint endpoint) {
        if (PROD_WORKERS.contains(endpoint))
            return PROD;
        if (QA_WORKERS.contains(endpoint))
            return QA;
        if (LOCAL_WORKERS.contains(endpoint))
            return LOCAL;
        throw new IllegalArgumentException("no stage defined for " + endpoint);
    }
}
