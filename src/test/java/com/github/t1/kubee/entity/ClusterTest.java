package com.github.t1.kubee.entity;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTERS;
import static com.github.t1.kubee.TestData.CLUSTER_A1;
import static org.assertj.core.api.Assertions.assertThat;

public class ClusterTest {
    @SneakyThrows(URISyntaxException.class)
    public static List<Cluster> readClusterConfig() {
        Path configPath = Paths.get(ClusterTest.class.getResource("test-cluster-config.yaml").toURI());
        return new ClusterStore(configPath).getClusters();
    }

    @Test void shouldReadYamlConfig() {
        List<Cluster> clusters = readClusterConfig();

        assertThat(clusters).containsExactly(CLUSTERS);
    }

    @Test void shouldProduceAllUris() {
        Stream<URI> uris = CLUSTER_A1.stages().flatMap(stage -> stage.nodes(CLUSTER_A1)).map(ClusterNode::uri);
        assertThat(uris).containsExactly(
            URI.create("http://server-a" + "dev01.server.lan:8180"),
            URI.create("http://server-a" + "dev02.server.lan:8180"),
            URI.create("http://qa" + "server-a01.server.lan:8180"),
            URI.create("http://qa" + "server-a02.server.lan:8180"),
            URI.create("http://server-a01.server.lan:8180"),
            URI.create("http://server-a02.server.lan:8180"),
            URI.create("http://server-a03.server.lan:8180")
        );
    }
}
