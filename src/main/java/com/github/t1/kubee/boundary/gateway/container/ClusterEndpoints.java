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
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Log
@RequiredArgsConstructor
class ClusterEndpoints {
    private final @NonNull Cluster cluster;
    private final @NonNull Path dockerComposeDir;

    private final Map<Stage, List<Endpoint>> actualEndpoints = new LinkedHashMap<>();

    @Override public String toString() { return actualEndpoints.toString(); }

    List<Endpoint> get(Stage stage) {
        // not computeIfAbsent, as refreshEndpointsFor already add the new value
        if (actualEndpoints.containsKey(stage))
            return actualEndpoints.get(stage);
        return refreshEndpointsFor(stage);
    }

    List<Endpoint> refreshEndpointsFor(Stage stage) {
        log.info("refresh endpoints for '" + stage.getName() + "'");
        List<Endpoint> endpoints = readProcessIdsFor(stage)
            .map(this::getEndpointFor)
            .collect(toList());
        actualEndpoints.put(stage, endpoints);
        log.info("endpoints for '" + stage.getName() + "': " + endpoints);
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
        // TODO this shouldn't know about the logical names in the cluster layer
        return new ClusterNode(cluster, cluster.findStage(docker.name), docker.number) // TODO simplify
            .endpoint().withPort(docker.port);
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
            "0\\.0\\.0\\.0:(?<exposedPort>\\d+)->(?<internalPort>\\d+)/tcp\t" +
            "docker_(?<name>.*?)_(?<number>\\d+)");
        Matcher matcher = pattern.matcher(ports);
        if (!matcher.matches())
            throw new RuntimeException("can't parse docker info from `" + ports + "`");
        if (intGroup(matcher, "internalPort") != publishPort)
            throw new RuntimeException("container " + containerId + " uses wrong internal port " + matcher.group("internalPort")
                + "; expected " + publishPort);
        return DockerInfo.builder()
            .port(intGroup(matcher, "exposedPort"))
            .number(intGroup(matcher, "number"))
            .name(matcher.group("name"))
            .build();
    }

    private int intGroup(Matcher matcher, String exposedPort) { return Integer.parseInt(matcher.group(exposedPort)); }

    @Value @Builder
    private static class DockerInfo {
        int number;
        int port;
        String name;
    }
}
