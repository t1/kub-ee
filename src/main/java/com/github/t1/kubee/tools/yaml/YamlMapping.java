package com.github.t1.kubee.tools.yaml;

import lombok.AllArgsConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.yaml.YamlNode.NULL_NODE;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static lombok.AccessLevel.PACKAGE;
import static org.yaml.snakeyaml.nodes.Tag.MAP;

@AllArgsConstructor(access = PACKAGE)
public class YamlMapping {
    static final YamlMapping EMPTY_MAPPING = new YamlMapping(null);

    private MappingNode node;

    @Override public String toString() {
        return "{\n" + stream().map(YamlEntry::toString).collect(joining(",\n")) + "\n}";
    }

    public boolean isEmpty() { return node == null || node.getValue().isEmpty(); }

    public boolean hasKey(String key) {
        return stream().anyMatch(entry -> entry.key().asString().equals(key));
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
        return stream().collect(toMap(entry -> entry.key().asString(), entry -> entry.value().asString()));
    }

    public Stream<YamlEntry> stream() { return (node == null) ? Stream.of() : node.getValue().stream().map(YamlEntry::new); }

    public void add(String key, String value) { add(key, scalar(value)); }

    private void add(String key, Node value) {
        if (node == null)
            node = new MappingNode(Tag.MAP, new ArrayList<>(), false);
        node.getValue().add(new NodeTuple(scalar(key), value));
    }

    private ScalarNode scalar(String value) {
        return new ScalarNode(Tag.STR, value, null, null, '\"');
    }

    public YamlMapping getMapping(String key) { return get(key).asMapping(); }

    public YamlMapping getOrCreateMapping(String key) {
        if (!hasKey(key))
            add(key, new MappingNode(MAP, new ArrayList<>(), false));
        return getMapping(key);
    }

    public void remove(String key) {
        if (node != null)
            node.getValue().removeIf(tuple -> ((ScalarNode) tuple.getKeyNode()).getValue().equals(key));
    }
}
