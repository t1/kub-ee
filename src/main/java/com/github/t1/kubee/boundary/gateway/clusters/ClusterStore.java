package com.github.t1.kubee.boundary.gateway.clusters;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import com.github.t1.kubee.tools.yaml.YamlMapping;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.Cluster.readAllFrom;
import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.nio.charset.StandardCharsets.UTF_8;

@RequestScoped
@NoArgsConstructor
@SuppressWarnings("CdiInjectionPointsInspection")
public class ClusterStore {
    @Inject @ConfigProperty Optional<Path> clusterConfigPath = Optional.empty();

    public ClusterStore(Path clusterConfigPath) {
        this.clusterConfigPath = Optional.ofNullable(clusterConfigPath);
    }

    public Stream<Cluster> clusters() { return getClusters().stream(); }

    public List<Cluster> getClusters() {
        return readAllFrom(readDocument(getConfigPath()), System.err::println);
    }

    private Path getConfigPath() {
        if (clusterConfigPath.isPresent())
            return clusterConfigPath.get();
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
        updateStage(node, stage -> {
            YamlMapping status = stage.getOrCreateMapping("status");
            String key = key(node, deploymentName);
            if (!status.hasKey(key))
                status.add(key, unbalanced.name());
        });
    }

    public void balance(ClusterNode node, String deploymentName) {
        updateStage(node, stage -> {
            if (stage.hasKey("status")) {
                YamlMapping status = stage.getMapping("status");
                status.remove(key(node, deploymentName));
                if (status.isEmpty())
                    stage.remove("status");
            }
        });
    }

    private void updateStage(ClusterNode node, Consumer<YamlMapping> consumer) {
        YamlDocument document = readDocument(getConfigPath());
        YamlMapping yamlNode = document.asMapping().getMapping(node.getCluster().id());
        YamlMapping stage = yamlNode.getMapping(node.getStage().getName());
        consumer.accept(stage);
        writeDocument(document, getConfigPath());
    }

    private void writeDocument(YamlDocument document, Path path) {
        try (OutputStream stream = Files.newOutputStream(path)) {
            stream.write(document.toString().getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("can't write cluster config file: " + path, e);
        }
    }

    private String key(ClusterNode node, String deploymentName) {
        return node.getIndex() + ":" + deploymentName;
    }
}
