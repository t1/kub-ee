package com.github.t1.kubee.tools.yaml;

import lombok.RequiredArgsConstructor;
import org.yaml.snakeyaml.nodes.*;

import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.yaml.YamlMapping.*;
import static com.github.t1.kubee.tools.yaml.YamlSequence.*;
import static java.util.Collections.*;
import static lombok.AccessLevel.*;
import static org.yaml.snakeyaml.nodes.NodeId.*;

@RequiredArgsConstructor(access = PACKAGE)
public class YamlNode {
    static final YamlNode NULL_NODE = new YamlNode(null);

    private final Node node;

    @Override public String toString() {
        if (node == null)
            return "NullNode";
        StringBuilder out = new StringBuilder();
        out.append(node.getNodeId()).append(":");
        switch (node.getNodeId()) {
        case scalar:
            out.append('\'').append(asString()).append('\'');
            break;
        case sequence:
            out.append(asSequence());
            break;
        case mapping:
            out.append(asMapping());
            break;
        case anchor:
            out.append("anchor");
            break;
        }
        return out.toString();
    }


    public YamlTag getTag() { return new YamlTag(node.getTag()); }


    public boolean isNull() { return node == null; }

    public boolean isEmpty() { return isNull() || (node.getNodeId() == scalar && asString().isEmpty()); }

    public void ifPresent(Consumer<YamlNode> consumer) {
        if (!isNull())
            consumer.accept(this);
    }


    public String asStringOr(String defaultValue) { return (node == null) ? defaultValue : asString(); }

    public String asString() {
        assert node.getNodeId() == scalar : "expected " + scalar + " but got " + node.getNodeId();
        return ((ScalarNode) node).getValue();
    }

    public int asIntOr(int defaultValue) { return (node == null) ? defaultValue : asInt(); }

    public int asInt() {
        assert node != null;
        return Integer.parseInt(asString());
    }


    public Stream<YamlNode> sequence() { return asSequence().stream(); }

    public YamlSequence asSequence() {
        if (isEmpty())
            return EMPTY_SEQUENCE;
        assert node == null || node.getNodeId() == sequence : "expected " + this + " to be a " + sequence;
        return new YamlSequence((node == null) ? emptyList() : ((SequenceNode) node).getValue());
    }


    public Stream<YamlEntry> mapping() { return asMapping().stream(); }

    public YamlMapping asMapping() {
        if (isEmpty())
            return EMPTY_MAPPING;
        assert node == null || node.getNodeId() == mapping : "expected " + this + " to be a " + mapping;
        return new YamlMapping((node == null) ? emptyList() : ((MappingNode) node).getValue());
    }

    public Map<String, String> asStringMap() { return asMapping().asStringMap(); }
}
