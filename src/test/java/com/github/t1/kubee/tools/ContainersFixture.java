package com.github.t1.kubee.tools;

import com.github.t1.kubee.TestData;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.Script;
import com.github.t1.kubee.tools.cli.Script.Result;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.LOCAL;
import static com.github.t1.kubee.TestData.LOCAL_ENDPOINTS;
import static com.github.t1.kubee.TestData.PROD;
import static com.github.t1.kubee.TestData.PROD_ENDPOINTS;
import static com.github.t1.kubee.TestData.QA;
import static com.github.t1.kubee.TestData.QA_ENDPOINTS;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@Accessors(chain = true)
public class ContainersFixture implements BeforeEachCallback, AfterEachCallback, Extension {

    @Setter @Getter private Path dockerComposeDir = Paths.get("src/test/docker/");

    private final List<Container> containers = new ArrayList<>();
    private final Map<String, Result> scaleResults = new HashMap<>();

    private final Script.Invoker originalProcessInvoker = Script.Invoker.INSTANCE;
    private final Script.Invoker invokerMock = new Script.Invoker() {
        @Override public Result invoke(Path workingDirectory, String commandline) {
            Parser parser = new Parser(commandline);
            if (parser.eats("docker-compose ")) {
                assertThat(workingDirectory).isEqualTo(dockerComposeDir);
                if (parser.eats("ps "))
                    return dockerComposePs(parser);
                if (parser.eats("up "))
                    return dockerComposeUp(parser);
                throw new RuntimeException("docker compose command not stubbed: " + commandline);
            }
            if (parser.eats("docker ")) {
                assertThat(workingDirectory).isNull();
                if (parser.eats("ps "))
                    return dockerPs(parser);
                throw new RuntimeException("docker command not stubbed: " + commandline);
            }
            throw new RuntimeException("commandline not stubbed: " + commandline);
        }

        private Result dockerComposePs(Parser parser) {
            assertThat(parser.eats("-q ")).isTrue();
            String serviceName = parser.eatRest();
            Stream<Container> containers = findContainersForService(serviceName);
            String containerIds = containers.map(Container::getId).collect(joining("\n"));
            return containerIds.isEmpty()
                ? new Result(1, "ERROR: No such service: " + serviceName)
                : new Result(0, containerIds);
        }

        private Result dockerComposeUp(Parser parser) {
            assertThat(parser.eats("--detach --scale ")).isTrue();
            String[] expression = parser.eatRest().split("=");
            String serviceName = expression[0];
            int target = parseInt(expression[1]);
            return scale(serviceName, target);
        }

        private Result dockerPs(Parser parser) {
            assertThat(parser.eats("--all ")).isTrue();
            assertThat(parser.eats("--format {{.Ports}}\t{{.Names}} ")).isTrue();
            assertThat(parser.eats("--filter id=")).isTrue();
            Container container = findContainerWithId(parser.eatWord());
            assertThat(parser.eats("--filter publish=8080")).isTrue();
            assertThat(parser.done()).isTrue();
            if (container.dockerPsResult != null)
                return container.dockerPsResult;
            else
                return new Result(0, "0.0.0.0:" + container.getInternalPort() + "->" + container.getExposedPort() + "/tcp\t" +
                    "docker_" + container.serviceName + "_" + container.index());
        }
    };

    private Result scale(String serviceName, int target) {
        Result preparedResult = scaleResults.get(serviceName);
        if (preparedResult != null)
            return preparedResult;
        List<Container> containers = findContainersForService(serviceName).collect(toList());
        List<Endpoint> endpoints = endpointsFor(serviceName);
        for (int i = containers.size(); i < target; i++)
            given(endpoints.get(i));
        for (int i = target; i < containers.size(); i++)
            ContainersFixture.this.containers.remove(containers.get(i));
        return new Result(0, "");
    }

    private static int nextPort = 33000;

    public void givenScaleResult(String serviceName, int exitValue, String output) {
        scaleResults.put(serviceName, new Result(exitValue, output));
    }

    @Getter public class Container {
        private final String id = UUID.randomUUID().toString();
        private final int port = nextPort++;
        @NonNull private final Endpoint endpoint;
        @NonNull private final String serviceName;
        private Result dockerPsResult;

        public Container(@NonNull Endpoint endpoint) {
            this.endpoint = endpoint;
            this.serviceName = serviceName(endpoint);
        }

        @Override public String toString() {
            return "[" + serviceName + ':' + port + " -> " + endpoint + ']';
        }

        public int index() { return endpointsFor(serviceName).indexOf(endpoint) + 1; }

        public int getInternalPort() { return port; }

        public int getExposedPort() { return endpoint.getPort(); }

        public void dockerInfo(int exitValue, String message) {
            dockerPsResult = new Result(exitValue, message);
        }
    }

    private Container findContainerWithId(@NonNull String id) {
        List<Container> containers = findContainers(container -> container.id.equals(id)).collect(toList());
        assertThat(containers.size()).describedAs("duplicate container id").isLessThanOrEqualTo(1);
        assertThat(containers).describedAs("container id not found").isNotEmpty();
        return containers.get(0);
    }

    private Stream<Container> findContainersForService(String serviceName) {
        return findContainers(container -> container.serviceName.equals(serviceName));
    }

    private Stream<Container> findContainers(Predicate<Container> filter) {
        return containers.stream().filter(filter);
    }

    @Override public void beforeEach(ExtensionContext context) { Script.Invoker.INSTANCE = invokerMock; }

    @Override public void afterEach(ExtensionContext context) { Script.Invoker.INSTANCE = originalProcessInvoker; }


    public void given(Endpoint... endpoints) { given(asList(endpoints)); }

    public void given(List<Endpoint> endpoints) {
        for (Endpoint endpoint : endpoints)
            given(endpoint);
    }

    public Container given(Endpoint endpoint) {
        Container container = new Container(endpoint);
        containers.add(container);
        return container;
    }

    private List<Endpoint> endpointsFor(String name) {
        switch (name) {
            case "local-worker":
                return LOCAL_ENDPOINTS;
            case "qa-worker":
                return QA_ENDPOINTS;
            case "worker":
                return PROD_ENDPOINTS;
        }
        throw new IllegalArgumentException("unknown stage " + name);
    }

    private String serviceName(Endpoint endpoint) { return stageOf(endpoint).serviceName(TestData.CLUSTER); }

    private Stage stageOf(Endpoint endpoint) {
        if (PROD_ENDPOINTS.contains(endpoint))
            return PROD;
        if (QA_ENDPOINTS.contains(endpoint))
            return QA;
        if (LOCAL_ENDPOINTS.contains(endpoint))
            return LOCAL;
        throw new IllegalArgumentException("no stage defined for " + endpoint);
    }

    public void verifyScaled(Stage stage, int scale) {
        assertThat(findContainersForService(stage.serviceName(TestData.CLUSTER)))
            .describedAs("stage " + stage + " to ")
            .hasSize(scale);
    }
}
