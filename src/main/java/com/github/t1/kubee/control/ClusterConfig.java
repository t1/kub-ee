package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import lombok.EqualsAndHashCode;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

@ApplicationScoped
@EqualsAndHashCode
public class ClusterConfig {
    public static final String FILE_NAME_PROPERTY = "kub-ee.cluster-config";

    private final List<Cluster> clusters = new ArrayList<>();

    @Override public String toString() {
        return clusters().map(Cluster::toString).collect(joining("\n"));
    }

    @Produces public List<Cluster> getClusters() { return clusters; }

    public Stream<Cluster> clusters() { return clusters.stream(); }

    @PostConstruct void read() {
        Path path = getConfigPath();
        this.clusters.addAll(readFrom(path));
    }

    private Path getConfigPath() {
        String file = System.getProperty(FILE_NAME_PROPERTY);
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

    public static List<Cluster> readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        return Cluster.readAllFrom(document, System.err::println);
    }
}
