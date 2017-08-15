package com.github.t1.kubee.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.NodeTuple;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static com.github.t1.kubee.tools.yaml.YamlNode.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static lombok.AccessLevel.*;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlMapping {
    static final YamlMapping EMPTY_MAPPING = new YamlMapping(emptyList());

    private final List<NodeTuple> entries;

    @Override public String toString() {
        return "{\n" + stream().map(YamlEntry::toString).collect(joining(",\n")) + "\n}";
    }

    public YamlNode get(String key) {
        return getOptional(key).orElse(NULL_NODE);
    }

    public Optional<YamlNode> getOptional(String key) {
        return stream()
                .flatMap(entry -> entry.withKey(key))
                .findAny();
    }

    public YamlMapping mapString(String name, Consumer<String> consumer) {
        return map(name, node -> consumer.accept(node.asString()));
    }

    public <U> YamlMapping mapSequence(String name, Consumer<List<U>> consumer, Function<YamlNode, U> function) {
        return map(name, node -> consumer.accept(node.asSequence().map(function).collect(toList())));
    }

    public YamlMapping map(String name, Consumer<YamlNode> consumer) {
        getOptional(name).ifPresent(consumer);
        return this;
    }

    public Map<String, String> asStringMap() {
        return stream().collect(Collectors.toMap(entry -> entry.key().asString(), entry -> entry.value().asString()));
    }

    public Stream<YamlEntry> stream() { return entries.stream().map(YamlEntry::new); }
}
