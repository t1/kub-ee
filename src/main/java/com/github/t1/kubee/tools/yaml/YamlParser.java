package com.github.t1.kubee.tools.yaml;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;

import java.io.StringReader;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Slf4j
public class YamlParser {
    public static Node parse(String yaml) { return new Yaml().compose(new StringReader(yaml)); }

    private static String asString(Node node) { return ((ScalarNode) node).getValue(); }

    private static List<Node> asSequence(Node node) { return ((SequenceNode) node).getValue(); }

    public static <T> List<T> mapSequence(Node node, Function<Node, T> function) {
        return asSequence(node).stream().map(function).collect(toList());
    }

    private static List<NodeTuple> asMapping(Node root) { return ((MappingNode) root).getValue(); }

    public static Map<String, String> asStringMap(Node node) {
        return asMapping(node).stream().collect(Collectors
                .toMap(n -> asString(n.getKeyNode()), n -> asString(n.getValueNode())));
    }

    @Builder
    public static class Mapping {
        @Singular private Map<String, Consumer<Node>> maps;

        public static <T> MappingBuilder<T> mapString(String name, Consumer<String> consumer) {
            return map(name, node -> consumer.accept(asString(node)));
        }

        public static <T> MappingBuilder<T> map(String name, Consumer<Node> consumer) {
            return Mapping.<T>builder().map(name, consumer);
        }

        public static class MappingBuilder<T> {
            public void from(Node node) { build().apply(node); }

            public MappingBuilder<T> mapString(String name, Consumer<String> consumer) {
                return map(name, node -> consumer.accept(asString(node)));
            }

            public <U> MappingBuilder<T> mapSequence(String name, Consumer<List<U>> consumer,
                    Function<Node, U> function) {
                return map(name, node -> consumer.accept(YamlParser.mapSequence(node, function)));
            }
        }

        private void apply(Node node) {
            for (NodeTuple field : asMapping(node)) {
                String key = asString(field.getKeyNode());
                Node value = field.getValueNode();
                maps.getOrDefault(key, v -> log.debug("unknown change field " + v))
                    .accept(value);
            }
        }
    }
}
