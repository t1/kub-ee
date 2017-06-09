package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.boundary.ClusterConfig;
import org.junit.Test;

import java.net.URI;
import java.util.stream.Stream;

import static com.github.t1.metadeployer.model.Slot.*;
import static org.assertj.core.api.Assertions.*;

public class ClusterTest {
    public static final Slot SLOT_0 = Slot.builder().name("0").http(8080).https(8443).build();
    public static final Slot SLOT_1 = Slot.builder().name("1").http(8180).https(8543).build();

    private static final Cluster[] CLUSTERS = {
            Cluster.builder().host("my.boss").slot(SLOT_0)
                   .stage().name("DEV").prefix("").suffix("dev").indexLength(2).count(1).add()
                   .stage().name("QA").prefix("qa").suffix("").indexLength(0).count(1).add()
                   .stage().name("PROD").prefix("").suffix("").indexLength(2).count(3).add()
                    .build(),
            Cluster.builder().host("other.boss").slot(DEFAULT_SLOT)
                   .stage().name("DEV").prefix("").suffix("").count(2).indexLength(2).add()
                    .build(),
            Cluster.builder().host("other.boss").slot(SLOT_1)
                   .stage().name("DEV").prefix("").suffix("").count(2).indexLength(2).add()
                    .build(),
            Cluster.builder().host("third.boss").slot(DEFAULT_SLOT)
                   .stage().name("").prefix("").suffix("").count(1).indexLength(2).add()
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
                URI.create("http://my" + "dev01.boss:8080"),
                URI.create("http://qa" + "my.boss:8080"),
                URI.create("http://my01.boss:8080"),
                URI.create("http://my02.boss:8080"),
                URI.create("http://my03.boss:8080")
        );
    }
}
