package com.github.t1.metadeployer.model;

import org.yaml.snakeyaml.nodes.*;

import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.*;
import static org.yaml.snakeyaml.nodes.NodeId.*;

public class YamlTools {
    public static String getScalarValue(Node node) { return getScalarValue(node, null); }

    public static String getScalarValue(Node node, String defaultValue) {
        if (node == null)
            return defaultValue;
        assert node.getNodeId() == scalar : "expected " + scalar + " but got " + node.getNodeId();
        return ((ScalarNode) node).getValue();
    }

    public static Stream<NodeTuple> mappingValue(Node node) { return getMappingValue(node).stream(); }

    public static List<NodeTuple> getMappingValue(Node node) {
        if (node == null)
            return emptyList();
        assert node.getNodeId() == mapping : "expected " + mapping + " but got " + node.getNodeId();
        return ((MappingNode) node).getValue();
    }

    public static int toInt(Node node, int defaultValue) {
        return (node == null) ? defaultValue : Integer.parseInt(getScalarValue(node));
    }

    public static Node get(List<NodeTuple> nodeTuples, String key) {
        if (nodeTuples != null)
            for (NodeTuple tuple : nodeTuples)
                if (key.equals(getScalarValue(tuple.getKeyNode())))
                    return tuple.getValueNode();
        return null;
    }
}
