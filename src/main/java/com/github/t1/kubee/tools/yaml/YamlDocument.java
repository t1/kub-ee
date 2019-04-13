package com.github.t1.kubee.tools.yaml;

import lombok.SneakyThrows;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.emitter.Emitter;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.resolver.Resolver;
import org.yaml.snakeyaml.serializer.Serializer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import static org.yaml.snakeyaml.DumperOptions.ScalarStyle.PLAIN;

public final class YamlDocument extends YamlNode {
    public static YamlDocument from(Reader reader) {
        Node root = new Yaml().compose(reader);
        return new YamlDocument(root);
    }

    private YamlDocument(Node node) { super(node); }

    @SneakyThrows(IOException.class)
    @Override public String toString() {
        Writer output = new StringWriter();
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultScalarStyle(PLAIN);
        Emitter emitter = new Emitter(output, dumperOptions);
        Serializer serializer = new Serializer(emitter, new Resolver(), dumperOptions, node.getTag());
        try {
            serializer.open();
            serializer.serialize(node);
        } finally {
            serializer.close();
        }
        // FIXME this really sucks:
        return output.toString().replace("\"", "").replace("!!int ", "");
    }
}
