package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.tools.cli.Script;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

@Log
@RequiredArgsConstructor
class DockerCommands {
    private final @NonNull Path dockerComposeDir;
    private Map<String, List<Integer>> cache;

    Map<String, List<Integer>> getDockerPorts() {
        if (cache == null)
            cache = read();
        return cache;
    }

    private Map<String, List<Integer>> read() {
        log.info("read endpoints");
        String output = new Script("docker ps --all --format {{.Names}}\t{{.Ports}}").run();
        if (output.isEmpty())
            return emptyMap();
        Pattern pattern = Pattern.compile(""
            + "docker_(?<serviceName>.*?)_(?<nodeNumber>\\d+)\t"
            + "0\\.0\\.0\\.0:(?<exposedPort>\\d+)->(?<servicePort>\\d+)/tcp");
        return Stream.of(output.split("\n"))
            .map(pattern::matcher)
            .map(this::matches)
            .sorted(SERVICE_NAME_AND_NODE_NUMBER)
            .collect(groupingBy(
                matcher -> matcher.group("serviceName"),
                mapping(matcher -> parseInt(matcher.group("exposedPort")),
                    toList())));
    }

    private Matcher matches(Matcher matcher) {
        if (!matcher.matches()) {
            StringBuffer buffer = new StringBuffer("can't parse docker info from `");
            matcher.appendTail(buffer);
            buffer.append("`");
            throw new RuntimeException(buffer.toString());
        }
        return matcher;
    }

    void scale(Map<String, Integer> scales) {
        Map<String, Integer> all = new LinkedHashMap<>(scales);
        addMissingScales(all);
        String scaleExpression = toScaleExpression(all);
        new Script("docker-compose up --no-color --quiet-pull --detach" + scaleExpression)
            .in(dockerComposeDir)
            .run();
        cache = null;
    }

    /**
     * There may be services in docker that are not managed by this cluster,
     * or if no change is required
     */
    private void addMissingScales(Map<String, Integer> scales) {
        getDockerPorts().forEach((serviceName, ports) -> {
            if (!scales.containsKey(serviceName)) {
                scales.put(serviceName, ports.size());
            }
        });
    }

    private String toScaleExpression(Map<String, Integer> all) {
        StringBuilder scaleExpression = new StringBuilder();
        all.forEach((serviceName, nodes) -> scaleExpression.append(" --scale ").append(serviceName).append("=").append(nodes));
        return scaleExpression.toString();
    }

    private static final Comparator<Matcher> SERVICE_NAME_AND_NODE_NUMBER = Comparator
        .<Matcher, String>comparing(matcher -> matcher.group("serviceName"))
        .thenComparingInt(matcher -> parseInt(matcher.group("nodeNumber")));
}
