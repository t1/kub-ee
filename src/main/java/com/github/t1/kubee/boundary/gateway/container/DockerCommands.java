package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.tools.cli.Script;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;

@Log
@RequiredArgsConstructor
class DockerCommands {
    private final @NonNull Path dockerComposeDir;

    Stream<Integer> getDockerPorts(String serviceName) {
        log.info("read endpoints for '" + serviceName + "'");
        String output = new Script("docker ps --all --format {{.Names}}\t{{.Ports}}").run();
        if (output.isEmpty())
            return Stream.empty();
        Pattern pattern = Pattern.compile(""
            + "docker_(?<serviceName>.*?)_(?<number>\\d+)\t"
            + "0\\.0\\.0\\.0:(?<exposedPort>\\d+)->(?<servicePort>\\d+)/tcp");
        return Stream.of(output.split("\n"))
            .map(pattern::matcher)
            .map(this::matches)
            .filter(matcher -> serviceName.equals(matcher.group("serviceName")))
            .map(matcher -> parseInt(matcher.group("exposedPort")));
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

    void scale(List<String> scaleExpressions) {
        new Script("docker-compose up --no-color --quiet-pull --detach --scale "
            + String.join(" --scale ", scaleExpressions))
            .in(dockerComposeDir)
            .run();
    }
}
