package com.github.t1.kubee.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.Node;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static lombok.AccessLevel.PACKAGE;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlSequence {
    static final YamlSequence EMPTY_SEQUENCE = new YamlSequence(emptyList());

    private final List<Node> nodes;

    public Stream<YamlNode> stream() { return nodes.stream().map(YamlNode::new); }

    @Override public String toString() { return stream().map(YamlNode::toString).collect(joining(",", "{", "}")); }

    public <U> Stream<U> map(Function<YamlNode, U> function) { return stream().map(function); }
}
