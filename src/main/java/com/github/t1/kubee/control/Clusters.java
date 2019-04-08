package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.tools.yaml.YamlDocument;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Clusters {
    @Produces @RequestScoped public List<Cluster> getClusters() {
        Path path = getConfigPath();
        return readFrom(path);
    }

    private Path getConfigPath() {
        String file = System.getProperty("kub-ee.cluster-config");
        if (file != null)
            return Paths.get(file);
        String root = System.getProperty("jboss.server.config.dir");
        if (root == null)
            root = System.getProperty("user.dir");
        return Paths.get(root, "cluster-config.yaml");
    }

    public static List<Cluster> readFrom(Path path) {
        try (InputStream stream = Files.newInputStream(path)) {
            return readFrom(stream);
        } catch (IOException e) {
            throw new IllegalStateException("can't read cluster config file: " + path);
        }
    }

    private static List<Cluster> readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        return Cluster.readAllFrom(document, System.err::println);
    }
}
