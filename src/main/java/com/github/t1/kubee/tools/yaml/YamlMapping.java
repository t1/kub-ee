package com.github.t1.kubee.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.NodeTuple;

import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.yaml.YamlNode.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.*;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlMapping {
    static final YamlMapping EMPTY_MAPPING = new YamlMapping(emptyList());

    private final List<NodeTuple> entries;

    public YamlNode get(String key) {
        return stream()
                .flatMap(entry -> entry.withKey(key))
                .findFirst()
                .orElse(NULL_NODE);
    }

    public Stream<YamlEntry> stream() {
        return entries.stream().map(YamlEntry::new);
    }

    @Override public String toString() {
        return "{\n" + stream().map(YamlEntry::toString).collect(joining(",\n")) + "\n}";
    }
}
