package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Cluster;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Node;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ClusterConfig {
    private final List<Cluster> clusters = new ArrayList<>();

    @Produces List<Cluster> clusters() { return clusters; }

    @PostConstruct void read() throws IOException {
        readFrom(Files.newInputStream(Paths.get("cluster-config.yaml")));
    }

    public void readFrom(InputStream stream) {
        Node node = new Yaml().compose(new InputStreamReader(stream));
        clusters.addAll(Cluster.readAllFrom(node));
    }
}
