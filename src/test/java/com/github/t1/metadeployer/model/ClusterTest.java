package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.boundary.ClusterConfig;
import org.junit.Test;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

public class ClusterTest {
    private static final Cluster[] CLUSTERS = {
            Cluster.builder().name("my.boss").port(8080)
                   .stage().name("DEV").prefix("").suffix(".dev").count(1).indexLength(2).add()
                   .stage().name("QA").prefix("qa.").suffix("").count(1).indexLength(0).add()
                   .stage().name("PROD").prefix("").suffix("").count(3).indexLength(2).add()
                    .build(),
            Cluster.builder().name("other.boss").port(80)
                   .stage().name("DEV").prefix("").suffix("").count(2).indexLength(0).add()
                    .build(),
            Cluster.builder().name("third.boss").port(80)
                   .stage().name("").prefix("").suffix("").count(1).indexLength(0).add()
                    .build()
    };

    public static ClusterConfig readClusterConfig() {
        ClusterConfig clusterConfig = new ClusterConfig();
        clusterConfig.readFrom(ClusterTest.class.getResourceAsStream("cluster-config.yaml"));
        return clusterConfig;
    }

    @Test
    public void shouldReadYamlConfig() throws Exception {
        ClusterConfig clusterConfig = readClusterConfig();

        assertThat(clusterConfig.clusters()).containsOnly(CLUSTERS);
    }

    @Test
    public void shouldProduceAllUris() throws Exception {
        Cluster cluster = CLUSTERS[0];
        Stream<URI> uris = cluster.stages().flatMap(stage -> stage.nodes(cluster)).map(ClusterNode::uri);
        assertThat(uris).containsOnly(
                URI.create("http://my.boss.dev01:8080"),
                URI.create("http://qa.my.boss:8080"),
                URI.create("http://my.boss01:8080"),
                URI.create("http://my.boss02:8080"),
                URI.create("http://my.boss03:8080")
        );
    }
}
