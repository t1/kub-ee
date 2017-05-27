package com.github.t1.metadeployer.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.NodeTuple;

import java.util.stream.Stream;

import static lombok.AccessLevel.*;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlEntry {
    private final NodeTuple tuple;

    public YamlNode key() { return new YamlNode(tuple.getKeyNode()); }

    public YamlNode value() { return new YamlNode(tuple.getValueNode()); }

    @Override public String toString() { return "<" + key() + "->" + value() + ">"; }

    public Stream<YamlNode> withKey(String key) {
        return key().asString().equals(key) ? Stream.of(value()) : Stream.empty();
    }
}
