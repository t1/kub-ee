package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Log
@NoArgsConstructor(force = true)
public class ClusterStatus {
    private final Cluster cluster;
    private final ProcessInvoker proc;
    private final Path dockerComposeDir;
    private final List<Endpoint> actualContainers;

    ClusterStatus(
        @NonNull Cluster cluster,
        @NonNull Path dockerComposeDir
    ) {
        this.cluster = cluster;
        this.proc = ProcessInvoker.INSTANCE;
        this.dockerComposeDir = dockerComposeDir;
        this.actualContainers = readDockerStatus();
    }

    private List<Endpoint> readDockerStatus() {
        List<String> containerIds = readDockerComposeProcessIdsFor(cluster.getSimpleName());
        return containerIds.stream().map(this::getEndpointFor).collect(toList());
    }

    private List<String> readDockerComposeProcessIdsFor(String name) {
        String output = proc.invoke(dockerComposeDir, "docker-compose", "ps", "-q", name);
        return output.isEmpty() ? emptyList() : asList(output.split("\n"));
    }

    private Endpoint getEndpointFor(String containerId) {
        SimpleEntry<Integer, Integer> indexToPort = readExposedPortFor(containerId, cluster.getSlot().getHttp(), cluster.getSimpleName());
        int index = indexToPort.getKey();
        int port = indexToPort.getValue();
        Stage stage = cluster.getStages().get(0); // TODO
        return new ClusterNode(cluster, stage, index).endpoint().withPort(port);
    }

    private SimpleEntry<Integer, Integer> readExposedPortFor(String containerId, int publishPort, String clusterName) {
        String ports = proc.invoke("docker", "ps", "--all", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + publishPort);
        if (ports.isEmpty())
            throw new RuntimeException("no docker status for container " + containerId);
        if (ports.startsWith("\t"))
            throw new RuntimeException("container seems to be down: " + containerId);
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
            scale(stage.getPrefix() + cluster.getSimpleName() + stage.getSuffix(), stage.getCount());
        return actualContainers;
    }

    private void scale(String name, int count) {
        log.info("Scale '" + name + "' from " + actualContainers.size() + " to " + count);
        String scaleExpression = name + "=" + count;
        proc.invoke(dockerComposeDir, "docker-compose", "up", "--detach", "--scale", scaleExpression);
        actualContainers.clear();
        actualContainers.addAll(readDockerStatus());
    }
}
