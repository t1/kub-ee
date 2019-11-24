package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.tools.cli.Script;
import com.github.t1.kubee.tools.cli.Script.Result;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.stream.Collectors.toList;

@Log
@RequiredArgsConstructor
class DockerCommands {
    private final @NonNull Path dockerComposeDir;

    private final Map<String, List<Integer>> actualEndpoints = new LinkedHashMap<>();

    @Override public String toString() { return actualEndpoints.toString(); }

    List<Integer> getDockerPorts(String serviceName) {
        // not computeIfAbsent, as refreshEndpointsFor already adds the new value
        if (actualEndpoints.containsKey(serviceName))
            return actualEndpoints.get(serviceName);
        return refreshEndpointsFor(serviceName);
    }

    List<Integer> refreshEndpointsFor(String serviceName) {
        log.info("refresh endpoints for '" + serviceName + "'");
        List<Integer> endpoints = readProcessIdsFor(serviceName)
            .map(this::getExposedPortFor)
            .collect(toList());
        actualEndpoints.put(serviceName, endpoints);
        log.info("endpoints for '" + serviceName + "': " + endpoints);
        return endpoints;
    }

    private Stream<String> readProcessIdsFor(String serviceName) {
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

    // TODO read all at once
    private int getExposedPortFor(String containerId) {
        String ports = new Script("docker ps --all --format {{.Ports}} --filter id=" + containerId)
            .run();
        if (ports.isEmpty())
            throw new RuntimeException("no docker status for container " + containerId);
        if (ports.startsWith("\t"))
            throw new RuntimeException("container seems to be down: " + containerId);
        Pattern pattern = Pattern.compile("0\\.0\\.0\\.0:(?<exposedPort>\\d+)->(?<servicePort>\\d+)/tcp");
        Matcher matcher = pattern.matcher(ports);
        if (!matcher.matches())
            throw new RuntimeException("can't parse docker info from `" + ports + "`");
        return parseInt(matcher.group("exposedPort"));
    }

    void scale(String scaleExpression) {
        new Script("docker-compose up --no-color --quiet-pull --detach --scale " + scaleExpression)
            .workingDirectory(dockerComposeDir)
            .run();
    }
}
