package com.github.t1.kubee.boundary.gateway.clusters;

import com.github.t1.kubee.boundary.config.ClusterConfigPath;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.tools.yaml.YamlDocument;
import com.github.t1.kubee.tools.yaml.YamlMapping;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.Cluster.readAllFrom;
import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.nio.charset.StandardCharsets.UTF_8;

@RequestScoped
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStore {
    @Inject @ClusterConfigPath Path clusterConfigPath;

    public Stream<Cluster> clusters() { return getClusters().stream(); }

    public List<Cluster> getClusters() {
        return readAllFrom(readDocument(), System.err::println);
    }

    private YamlDocument readDocument() {
        try (InputStream stream = Files.newInputStream(clusterConfigPath)) {
            return YamlDocument.from(new InputStreamReader(stream));
        } catch (IOException e) {
            throw new RuntimeException("can't read cluster config file: " + clusterConfigPath, e);
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
        YamlDocument document = readDocument();
        YamlMapping yamlNode = document.asMapping().getMapping(node.getCluster().id());
        YamlMapping stage = yamlNode.getMapping(node.getStage().getName());
        consumer.accept(stage);
        writeDocument(document);
    }

    private void writeDocument(YamlDocument document) {
        try (OutputStream stream = Files.newOutputStream(clusterConfigPath)) {
            stream.write(document.toString().getBytes(UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("can't write cluster config file: " + clusterConfigPath, e);
        }
    }

    private String key(ClusterNode node, String deploymentName) {
        return node.getIndex() + ":" + deploymentName;
    }
}
