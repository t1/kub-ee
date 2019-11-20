package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.ProcessInvoker;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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
        return cluster.stages()
            .flatMap(this::readProcessIdsFor)
            .map(this::getEndpointFor)
            .collect(toList());
    }

    private Stream<String> readProcessIdsFor(Stage stage) {
        String output = proc.invoke(dockerComposeDir, "docker-compose", "ps", "-q", stage.serviceName(cluster));
        return output.isEmpty() ? Stream.empty() : Stream.of(output.split("\n"));
    }

    private Endpoint getEndpointFor(String containerId) {
        DockerInfo docker = readExposedPortFor(containerId, cluster.getSlot().getHttp());
        return new ClusterNode(cluster, cluster.findStage(docker.name), docker.index)
            .endpoint().withPort(docker.port);
    }

    @Value @Builder
    private static class DockerInfo {
        int index;
        int port;
        String name;
    }

    private DockerInfo readExposedPortFor(String containerId, int publishPort) {
        String ports = proc.invoke("docker", "ps", "--all", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=" + publishPort);
        if (ports.isEmpty())
            throw new RuntimeException("no docker status for container " + containerId);
        if (ports.startsWith("\t"))
            throw new RuntimeException("container seems to be down: " + containerId);
        Pattern pattern = Pattern.compile("0\\.0\\.0\\.0:(?<port>\\d+)->" + publishPort + "/tcp\tdocker_(?<name>.*?)_(?<index>\\d+)");
        Matcher matcher = pattern.matcher(ports);
        if (!matcher.matches())
            throw new RuntimeException("can't parse docker info from `" + ports + "`");
        return DockerInfo.builder()
            .port(Integer.parseInt(matcher.group("port")))
            .index(Integer.parseInt(matcher.group("index")))
            .name(matcher.group("name"))
            .build();
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
            scale(stage.serviceName(cluster), stage.getCount());
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
