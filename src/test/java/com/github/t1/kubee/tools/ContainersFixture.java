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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTER;
import static java.lang.Integer.parseInt;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

@Accessors(chain = true)
public class ContainersFixture implements BeforeEachCallback, AfterEachCallback, Extension {

    @Setter @Getter private Path dockerComposeDir = Paths.get("src/test/docker/");

    private final List<Container> containers = new ArrayList<>();
    private Result scaleResult = null;
    private Result dockerPsResult = null;

    private final Script.Invoker originalProcessInvoker = Script.Invoker.INSTANCE;
    private final Script.Invoker invokerMock = new Script.Invoker() {
        @Override public Result invoke(String commandline, Path workingDirectory, int timeout) {
            Parser parser = new Parser(commandline);
            if (parser.eats("docker ")) {
                assertThat(workingDirectory).isNull();
                if (parser.eats("ps "))
                    return dockerPs(parser);
                throw new RuntimeException("docker command not stubbed: " + commandline);
            }
            if (parser.eats("docker-compose ")) {
                assertThat(workingDirectory).isEqualTo(dockerComposeDir);
                if (parser.eats("up "))
                    return dockerComposeUp(parser);
                throw new RuntimeException("docker compose command not stubbed: " + commandline);
            }
            throw new RuntimeException("commandline not stubbed: " + commandline);
        }

        private Result dockerPs(Parser parser) {
            assertThat(parser.eats("--all ")).isTrue();
            assertThat(parser.eatRest()).isEqualTo("--format {{.Names}}\t{{.Ports}}");
            assertThat(parser.done()).isTrue();
            return (dockerPsResult != null) ? dockerPsResult
                : new Result(0, dockerPsOutput());
        }

        private String dockerPsOutput() {
            return containers.stream()
                .map(container -> ""
                    + "docker_" + container.serviceName + "_" + container.nodeNumber + "\t"
                    + "0.0.0.0:" + container.exposedPort + "->" + container.servicePort + "/tcp")
                .collect(joining("\n"));
        }

        private Result dockerComposeUp(Parser parser) {
            assertThat(parser.eats("--no-color --quiet-pull --detach ")).isTrue();
            if (scaleResult != null)
                return scaleResult;
            Set<String> otherServices = containers.stream().map(Container::getServiceName).collect(toSet());
            do
            {
                assertThat(parser.eats("--scale ")).isTrue();
                String[] expression = parser.eatWord().split("=");
                String serviceName = expression[0];
                int target = parseInt(expression[1]);
                otherServices.remove(serviceName);
                scale(serviceName, target);
            } while (!parser.done());
            otherServices.forEach(serviceName -> scale(serviceName, 1));
            return new Result(0, "");
        }
    };

    private void scale(String serviceName, int target) {
        List<Container> containers = findContainersForService(serviceName).collect(toList());
        for (int i = containers.size(); i < target; i++)
            given(nodesFor(serviceName).get(i));
        for (int i = target; i < containers.size(); i++)
            ContainersFixture.this.containers.remove(containers.get(i));
    }

    private static int nextPort = 33000;

    public void givenDockerPsResult(int exitValue, String output) {
        dockerPsResult = new Result(exitValue, output);
    }

    public void givenScaleResult(int exitValue, String output) {
        scaleResult = new Result(exitValue, output);
    }

    public Endpoint[] thoseEndpointsIn(Stage stage, int expectedCount) {
        List<Endpoint> endpoints = endpointsIn(stage);
        assertThat(endpoints).hasSize(expectedCount);
        return endpoints.toArray(new Endpoint[0]);
    }

    public List<Endpoint> endpointsIn(Stage stage) {
        return in(stage)
            .map(container -> new Endpoint(container.alias, container.exposedPort))
            .collect(toList());
    }

    private Stream<Container> in(Stage stage) {
        String serviceName = stage.serviceName(CLUSTER);
        return containers.stream()
            .filter(container -> container.serviceName.equals(serviceName));
    }

    @RequiredArgsConstructor
    @Getter public static class Container {
        private final String id = UUID.randomUUID().toString();
        @NonNull private final String alias;
        private final int nodeNumber;
        private final int exposedPort = nextPort++;
        private final int servicePort;
        @NonNull private final String serviceName;

        @Override public String toString() {
            return "[" + serviceName + ':' + exposedPort + " -> " + servicePort + ']';
        }
    }

    public Stream<Container> findContainersForService(String serviceName) {
        return findContainers(container -> container.serviceName.equals(serviceName));
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
        return given(node.host(), node.getNumber(), node.port(), node.serviceName());
    }

    public Container given(String alias, int nodeNumber, int servicePort, String serviceName) {
        Container container = new Container(alias, nodeNumber, servicePort, serviceName);
        containers.add(container);
        return container;
    }

    private List<ClusterNode> nodesFor(String serviceName) {
        return CLUSTER.findStage(serviceName).nodes(CLUSTER).collect(toList());
    }

    public void verifyScaled(Stage stage, int scale) {
        assertThat(findContainersForService(stage.serviceName(CLUSTER)))
            .describedAs("stage " + stage + " to ")
            .hasSize(scale);
    }
}
