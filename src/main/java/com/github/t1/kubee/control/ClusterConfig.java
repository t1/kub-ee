package com.github.t1.kubee.control;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

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

import static com.github.t1.kubee.tools.http.ProblemDetail.badRequest;
import static java.util.stream.Collectors.joining;

@Slf4j
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
        try (InputStream stream = Files.newInputStream(path)) {
            readFrom(stream);
        } catch (IOException e) {
            throw badRequest().detail("can't read cluster config file: " + path).exception();
        }
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

    public ClusterConfig readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        clusters.addAll(Cluster.readAllFrom(document, log::warn));
        return this;
    }
}
