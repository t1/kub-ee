package com.github.t1.metadeployer.tools.yaml;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;

import java.io.Reader;

public final class YamlDocument extends YamlNode {
    public static YamlDocument from(Reader reader) {
        Node root = new Yaml().compose(reader);
        return new YamlDocument(root);
    }

    private YamlDocument(Node node) { super(node); }

    @Override public String toString() { return "document:" + super.toString(); }
}
