package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Cluster;
import com.github.t1.metadeployer.tools.yaml.YamlDocument;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@ApplicationScoped
public class ClusterConfig {
    private final List<Cluster> clusters = new ArrayList<>();

    @Produces public List<Cluster> clusters() { return clusters; }

    @PostConstruct void read() {
        Path path = getConfigPath();
        try (InputStream stream = Files.newInputStream(path)) {
            readFrom(stream);
        } catch (IOException e) {
            throw new RuntimeException("can't read cluster config file: " + path);
        }
    }

    private Path getConfigPath() {
        String file = System.getProperty("meta-deployer.cluster-config");
        if (file != null)
            return Paths.get(file);
        String root = System.getProperty("jboss.server.config.dir");
        if (root == null)
            root = System.getProperty("user.dir");
        return Paths.get(root, "cluster-config.yaml");
    }

    public void readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        clusters.addAll(Cluster.readAllFrom(document));
    }
}
