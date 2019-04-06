package com.github.t1.kubee.gateway.container;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.Stage;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@NoArgsConstructor(force = true)
public class ClusterStatus {
    private final Consumer<String> note;
    private final Cluster cluster;
    private final ProcessInvoker proc;
    private final Path dockerComposeConfigPath;
    private final List<Endpoint> actualContainers;

    public ClusterStatus(
        @NonNull Consumer<String> note,
        @NonNull Cluster cluster,
        @NonNull Path dockerComposeConfigPath
    ) {
        this.note = note;
        this.cluster = cluster;
        this.proc = ProcessInvoker.INSTANCE;
        this.dockerComposeConfigPath = dockerComposeConfigPath;
        this.actualContainers = readDockerStatus(cluster);
    }

    private List<Endpoint> readDockerStatus(Cluster cluster) {
        List<String> containerIds = readDockerComposeProcessIdsFor(cluster.getSimpleName());
        return containerIds.stream()
            .map(containerId -> getEndpointFor(cluster, containerId))
            .collect(toList());
    }

    private List<String> readDockerComposeProcessIdsFor(String name) {
        String output = proc.invoke(dockerComposeConfigPath.getParent(), "docker-compose", "ps", "-q", name);
        return Arrays.asList(output.split("\n"));
    }

    private Endpoint getEndpointFor(Cluster cluster, String containerId) {
        SimpleEntry<Integer, Integer> indexToPort = readExposedPortFor(containerId, cluster.getSlot().getHttp(), cluster.getSimpleName());
        int index = indexToPort.getKey();
        int port = indexToPort.getValue();
        Stage stage = cluster.getStages().get(0); // TODO
        return new ClusterNode(cluster, stage, index).endpoint().withPort(port);
    }

    private SimpleEntry<Integer, Integer> readExposedPortFor(String containerId, int publishPort, String clusterName) {
        String ports = proc.invoke("docker", "ps", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + publishPort);
        Pattern pattern = Pattern.compile("0\\.0\\.0\\.0:(?<port>\\d+)->" + publishPort + "/tcp\tdocker_" + clusterName + "_(?<index>\\d+)");
        Matcher matcher = pattern.matcher(ports);
        if (!matcher.matches())
            throw new RuntimeException("can't parse port in `" + ports + "`");
        int port = Integer.parseInt(matcher.group("port"));
        int index = Integer.parseInt(matcher.group("index"));
        return new SimpleEntry<>(index, port);
    }


    public Integer port(String host) {
        return endpoints()
            .filter(endpoint -> endpoint.getHost().equals(host))
            .findFirst()
            .map(Endpoint::getPort)
            .orElse(null);
    }

    public Stream<Endpoint> endpoints() { return actualContainers.stream(); }

    public List<Endpoint> scale(Stage stage) {
        if (actualContainers.size() != stage.getCount())
            scale(stage.nodeBaseName(cluster), stage.getCount());
        return actualContainers;
    }

    private void scale(String name, int count) {
        note.accept("Scale '" + name + "' from " + actualContainers.size() + " to " + count);
        String scaleExpression = name + "=" + count;
        proc.invoke(dockerComposeConfigPath, "docker-compose", "up", "--detach", "--scale", scaleExpression);
        actualContainers.clear();
        actualContainers.addAll(readDockerStatus(cluster));
    }
}