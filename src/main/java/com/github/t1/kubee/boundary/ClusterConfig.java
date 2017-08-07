package com.github.t1.kubee.boundary;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import lombok.SneakyThrows;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static com.github.t1.kubee.model.Stage.*;
import static com.github.t1.kubee.tools.http.ProblemDetail.*;

@ApplicationScoped
public class ClusterConfig {
    public static final String FILE_NAME_PROPERTY = "kub-ee.cluster-config";
    private final List<Cluster> clusters = new ArrayList<>();

    @Produces public List<Cluster> clusters() { return clusters; }

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

    public void readFrom(InputStream stream) {
        YamlDocument document = YamlDocument.from(new InputStreamReader(stream));
        clusters.addAll(Cluster.readAllFrom(document));
    }

    public void write(Path path) { new ClusterWriter(path).write(); }

    public ClusterConfig add(Cluster cluster) {
        clusters.add(cluster);
        return this;
    }

    private class ClusterWriter {
        private final BufferedWriter out;

        @SneakyThrows(IOException.class)
        private ClusterWriter(Path path) {
            this.out = Files.newBufferedWriter(path);
        }

        @SneakyThrows(IOException.class)
        public void write() {
            clusters.stream().map(Cluster::getSlot).sorted().distinct().forEach(this::write);
            clusters.forEach(this::write);
            out.flush();
            out.close();
        }

        private void write(Slot slot) {
            append(":slot:").append(slot.getName()).append(":\n");
            if (slot.getHttp() > 0)
                append("  http: ").append(slot.getHttp()).append("\n");
            if (slot.getHttps() > 0)
                append("  https: ").append(slot.getHttps()).append("\n");
        }

        private void write(Cluster cluster) {
            append(cluster.id()).append(":\n");
            cluster.stages().forEach(this::write);
        }

        private void write(Stage stage) {
            append("  ").append(stage.getName()).append(":\n");
            if (!stage.getPrefix().isEmpty())
                append("    prefix: ").append(stage.getPrefix()).append("\n");
            if (!stage.getSuffix().isEmpty())
                append("    suffix: ").append(stage.getSuffix()).append("\n");
            if (stage.getIndexLength() > 0)
                append("    indexLength: ").append(stage.getIndexLength()).append("\n");
            append("    count: ").append(stage.getCount()).append("\n");
            if (!stage.getPath().equals(DEFAULT_PATH))
                append("    path: ").append(stage.getPath()).append("\n");
        }

        @SneakyThrows(IOException.class)
        private ClusterWriter append(Object text) {
            out.append(text.toString());
            return this;
        }
    }
}
