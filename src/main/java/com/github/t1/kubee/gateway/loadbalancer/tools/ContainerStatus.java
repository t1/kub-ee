package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import com.github.t1.nginx.HostPort;
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
    private final ProcessInvoker proc;
    private final Path dockerComposeConfigPath;
    private final List<HostPort> actualContainers;

    ContainerStatus(@NonNull ProcessInvoker proc, @NonNull Cluster cluster, @NonNull Path dockerComposeConfigPath) {
        this.proc = proc;
        this.dockerComposeConfigPath = dockerComposeConfigPath;
        this.actualContainers = readDockerStatus(cluster);
    }

    private List<HostPort> readDockerStatus(Cluster cluster) {
        List<String> containerIds = readDockerComposeProcessIdsFor(cluster.getSimpleName());
        return containerIds.stream()
            .map(containerId -> getHostPortFor(cluster, containerId))
            .collect(toList());
    }

    private List<String> readDockerComposeProcessIdsFor(String name) {
        String output = proc.invoke(dockerComposeConfigPath.getParent(), "docker-compose", "ps", "-q", name);
        return Arrays.asList(output.split("\n"));
    }

    private HostPort getHostPortFor(Cluster cluster, String containerId) {
        SimpleEntry<Integer, Integer> indexToPort = readExposedPortFor(containerId, cluster.getSlot().getHttp(), cluster.getSimpleName());
        int index = indexToPort.getKey();
        int port = indexToPort.getValue();
        String host = cluster.node(cluster.getStages().get(0), index).host();
        return new HostPort(host, port);
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
        actualContainers.add(node.hostPort().toHostPort());
    }

    Integer actualPort(String host) {
        return actualContainers.stream()
            .filter(hostPort -> hostPort.getHost().equals(host))
            .findFirst()
            .map(HostPort::getPort)
            .orElse(null);
    }

    void forEach(Consumer<HostPort> consumer) { actualContainers.forEach(consumer); }
}
