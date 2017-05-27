package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Cluster;
import com.github.t1.metadeployer.tools.yaml.YamlDocument;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ClusterConfig {
    private final List<Cluster> clusters = new ArrayList<>();

    @Produces public List<Cluster> clusters() { return clusters; }

    @PostConstruct void read() {
        Path path = Paths.get(System.getProperty("jboss.server.config.dir"), "cluster-config.yaml");
        try (InputStream stream = Files.newInputStream(path)) {
            readFrom(stream);
        } catch (IOException e) {
            throw new RuntimeException("can't read cluster config file: " + path);
        }
    }

    public void readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        clusters.addAll(Cluster.readAllFrom(document));
    }
}
