package com.github.t1.kubee.gateway.loadbalancer.tools.config;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import lombok.NonNull;

import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;

class ContainerStatus {
    private final Consumer<String> note;
    private final ProcessInvoker proc;
    private final Path dockerComposeConfigPath;
    private final List<Endpoint> actualContainers;

    ContainerStatus(
        @NonNull Consumer<String> note,
        @NonNull ProcessInvoker proc,
        @NonNull Cluster cluster,
        @NonNull Path dockerComposeConfigPath
    ) {
        this.note = note;
        this.proc = proc;
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
        return cluster.node(cluster.getStages().get(0), index).endpoint().withPort(port);
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


    void start(ClusterNode node) {
        note.accept("Start missing container " + node.endpoint());
        actualContainers.add(node.endpoint());
    }

    Integer actualPort(String host) {
        return actualContainers.stream()
            .filter(hostPort -> hostPort.getHost().equals(host))
            .findFirst()
            .map(Endpoint::getPort)
            .orElse(null);
    }

    void forEach(Consumer<Endpoint> consumer) { actualContainers.forEach(consumer); }

    void stop(Endpoint hostPort) {
        note.accept("Stopping excess container " + hostPort);
        actualContainers.remove(hostPort);
    }
}
