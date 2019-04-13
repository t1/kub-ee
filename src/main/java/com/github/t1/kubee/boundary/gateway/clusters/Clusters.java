package com.github.t1.kubee.boundary.gateway.clusters;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import com.github.t1.kubee.tools.yaml.YamlMapping;

import javax.enterprise.context.RequestScoped;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.nio.charset.StandardCharsets.UTF_8;

@RequestScoped
public class Clusters {
    public static List<Cluster> readFrom(Path path) {
        return Cluster.readAllFrom(readDocument(path), System.err::println);
    }

    public Stream<Cluster> stream() { return readFrom(getConfigPath()).stream(); }

    private Path getConfigPath() {
        String file = System.getProperty("kub-ee.cluster-config");
        if (file != null)
            return Paths.get(file);
        String root = System.getProperty("jboss.server.config.dir");
        if (root == null)
            root = System.getProperty("user.dir");
        return Paths.get(root, "cluster-config.yaml");
    }

    private static YamlDocument readDocument(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return YamlDocument.from(new InputStreamReader(stream));
        } catch (IOException e) {
            throw new RuntimeException("can't read cluster config file: " + path, e);
        }
    }

    public void unbalance(ClusterNode node, String deploymentName) {
        YamlDocument document = readDocument(getConfigPath());
        YamlMapping yamlNode = document.asMapping().getMapping(node.getCluster().id());
        YamlMapping stage = yamlNode.getMapping(node.getStage().getName());
        YamlMapping status = stage.getOrCreateMapping("status");
        status.add(node.getIndex() + ":" + deploymentName, unbalanced.name());
        writeDocument(document, getConfigPath());
    }

    private void writeDocument(YamlDocument document, Path path) {
        try (OutputStream stream = Files.newOutputStream(path)) {
            stream.write(document.toString().getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("can't write cluster config file: " + path, e);
        }
    }
}
