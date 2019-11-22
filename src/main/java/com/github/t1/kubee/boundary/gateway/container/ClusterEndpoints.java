package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.cli.Script;
import com.github.t1.kubee.tools.cli.Script.Result;
import lombok.Builder;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Value;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
class ClusterEndpoints {
    private final @NonNull Cluster cluster;
    private final @NonNull Path dockerComposeDir;

    private final Map<Stage, List<Endpoint>> actualEndpoints = new HashMap<>();

    List<Endpoint> get(Stage stage) {
        return actualEndpoints.computeIfAbsent(stage, this::refreshEndpointsFor);
    }

    List<Endpoint> refreshEndpointsFor(Stage stage) {
        List<Endpoint> endpoints = readProcessIdsFor(stage)
            .map(this::getEndpointFor)
            .collect(toList());
        actualEndpoints.put(stage, endpoints);
        return endpoints;
    }

    private Stream<String> readProcessIdsFor(Stage stage) {
        String serviceName = stage.serviceName(cluster);
        Script script = new Script("docker-compose ps -q " + serviceName)
            .workingDirectory(dockerComposeDir);
        Result result = script.runWithoutCheck();
        String output = result.getOutput();
        if (result.getExitValue() == 1) {
            if (("ERROR: No such service: " + serviceName).equals(output))
                output = "";
            else
                script.check(result);
        }
        return (output.isEmpty()) ? Stream.empty() : Stream.of(output.split("\n"));
    }

    private Endpoint getEndpointFor(String containerId) {
        DockerInfo docker = readDockerInfoFor(containerId, cluster.getSlot().getHttp());
        return new ClusterNode(cluster, cluster.findStage(docker.name), docker.index).endpoint();
    }

    private DockerInfo readDockerInfoFor(String containerId, int publishPort) {
        String ports = new Script("docker ps --all --format {{.Ports}}\t{{.Names}} " +
            "--filter id=" + containerId + " --filter publish=" + publishPort)
            .run();
        if (ports.isEmpty())
            throw new RuntimeException("no docker status for container " + containerId);
        if (ports.startsWith("\t"))
            throw new RuntimeException("container seems to be down: " + containerId);
        Pattern pattern = Pattern.compile("" +
            "0\\.0\\.0\\.0:(?<internalPort>\\d+)->(?<exposedPort>\\d+)/tcp\t" +
            "docker_(?<name>.*?)_(?<index>\\d+)");
        Matcher matcher = pattern.matcher(ports);
        if (!matcher.matches())
            throw new RuntimeException("can't parse docker info from `" + ports + "`");
        if (intGroup(matcher, "exposedPort") != publishPort)
            throw new RuntimeException("container " + containerId + " exposes wrong port " + matcher.group("exposedPort")
                + "; expected " + publishPort);
        return DockerInfo.builder()
            .port(intGroup(matcher, "internalPort"))
            .index(intGroup(matcher, "index"))
            .name(matcher.group("name"))
            .build();
    }

    private int intGroup(Matcher matcher, String exposedPort) { return Integer.parseInt(matcher.group(exposedPort)); }

    @Value @Builder
    private static class DockerInfo {
        int index;
        int port;
        String name;
    }
}
