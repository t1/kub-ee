package com.github.t1.kubee.tools;

import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.Script;
import com.github.t1.kubee.tools.cli.Script.Result;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
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

import static com.github.t1.kubee.TestData.CLUSTER;
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
        @Override public Result invoke(String commandline, Path workingDirectory, int timeout) {
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
            assertThat(parser.eats("--no-color --quiet-pull ")).isTrue();
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
                    "docker_" + container.serviceName() + "_" + container.number());
        }
    };

    private Result scale(String serviceName, int target) {
        Result preparedResult = scaleResults.get(serviceName);
        if (preparedResult != null)
            return preparedResult;
        List<Container> containers = findContainersForService(serviceName).collect(toList());
        List<ClusterNode> nodes = nodesFor(serviceName);
        for (int i = containers.size(); i < target; i++)
            given(nodes.get(i));
        for (int i = target; i < containers.size(); i++)
            ContainersFixture.this.containers.remove(containers.get(i));
        return new Result(0, "");
    }

    private static int nextPort = 33000;

    public void givenScaleResult(String serviceName, int exitValue, String output) {
        scaleResults.put(serviceName, new Result(exitValue, output));
    }

    public Endpoint[] thoseEndpointsIn(Stage stage, int expectedCount) {
        List<Endpoint> endpoints = endpointsIn(stage);
        assertThat(endpoints).hasSize(expectedCount);
        return endpoints.toArray(new Endpoint[0]);
    }

    public List<Endpoint> endpointsIn(Stage stage) {
        return in(stage)
            .map(container -> new Endpoint(container.node.host(), container.port))
            .collect(toList());
    }

    private Stream<Container> in(Stage stage) {
        return containers.stream()
            .filter(container -> container.node.getStage().equals(stage));
    }

    @RequiredArgsConstructor
    @Getter public static class Container {
        private final String id = UUID.randomUUID().toString();
        private final int port = nextPort++;
        @NonNull private final ClusterNode node;
        private Result dockerPsResult;

        @Override public String toString() {
            return "[" + serviceName() + ':' + port + " -> " + node + ']';
        }

        private String serviceName() { return node.serviceName(); }

        public int number() { return node.getNumber(); }

        public int getInternalPort() { return port; }

        public int getExposedPort() { return node.endpoint().getPort(); }

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
        return findContainers(container -> container.serviceName().equals(serviceName));
    }

    private Stream<Container> findContainers(Predicate<Container> filter) {
        return containers.stream().filter(filter);
    }

    @Override public void beforeEach(ExtensionContext context) { Script.Invoker.INSTANCE = invokerMock; }

    @Override public void afterEach(ExtensionContext context) { Script.Invoker.INSTANCE = originalProcessInvoker; }


    public void given() {}

    public void given(ClusterNode... nodes) { given(asList(nodes)); }

    public void given(List<ClusterNode> nodes) {
        for (ClusterNode node : nodes)
            given(node);
    }

    public Container given(ClusterNode node) {
        Container container = new Container(node);
        containers.add(container);
        return container;
    }

    private List<ClusterNode> nodesFor(String name) {
        return CLUSTER.findStage(name).nodes(CLUSTER).collect(toList());
    }

    public void verifyScaled(Stage stage, int scale) {
        assertThat(findContainersForService(stage.serviceName(CLUSTER)))
            .describedAs("stage " + stage + " to ")
            .hasSize(scale);
    }
}
