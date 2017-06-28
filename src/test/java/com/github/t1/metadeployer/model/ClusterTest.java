package com.github.t1.metadeployer.model;

import com.github.t1.metadeployer.boundary.ClusterConfig;
import org.junit.Test;

import java.net.URI;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

public class ClusterTest {
    public static final Slot SLOT_0 = Slot.builder().name("0").http(8080).https(8443).build();
    public static final Slot SLOT_1 = Slot.builder().name("1").http(8180).https(8543).build();
    public static final Slot SLOT_2 = Slot.builder().name("2").http(8280).https(8643).build();

    private static final Cluster[] CLUSTERS = {
            Cluster.builder().host("server-a.server.lan").slot(SLOT_1)
                   .stage().name("DEV").prefix("").suffix("dev").indexLength(2).count(2).add()
                   .stage().name("QA").prefix("qa").suffix("").indexLength(2).count(2).add()
                   .stage().name("PROD").prefix("").suffix("").indexLength(2).count(3).add()
                    .build(),
            Cluster.builder().host("server-a.server.lan").slot(SLOT_2)
                   .stage().name("DEV").prefix("").suffix("dev").count(2).indexLength(2).add()
                   .stage().name("QA").prefix("qa").suffix("").indexLength(2).count(2).add()
                   .stage().name("PROD").prefix("").suffix("").indexLength(2).count(3).add()
                    .build(),
            Cluster.builder().host("server-b.server.lan").slot(SLOT_2)
                   .stage().name("DEV").prefix("").suffix("test").count(2).indexLength(2).add()
                   .stage().name("QA").prefix("qa").suffix("").indexLength(2).count(2).add()
                   .stage().name("PROD").prefix("").suffix("").indexLength(2).count(3).add()
                    .build(),
            Cluster.builder().host("localhost").slot(SLOT_1)
                   .stage().name("PROD").prefix("").suffix("").count(1).indexLength(0).add()
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

        assertThat(clusterConfig.clusters()).containsExactly(CLUSTERS);
    }

    @Test
    public void shouldProduceAllUris() throws Exception {
        Cluster cluster = CLUSTERS[0];
        Stream<URI> uris = cluster.stages().flatMap(stage -> stage.nodes(cluster)).map(ClusterNode::uri);
        assertThat(uris).containsExactly(
                URI.create("http://server-a"+"dev01.server.lan:8180"),
                URI.create("http://server-a"+"dev02.server.lan:8180"),
                URI.create("http://qa"+"server-a01.server.lan:8180"),
                URI.create("http://qa"+"server-a02.server.lan:8180"),
                URI.create("http://server-a01.server.lan:8180"),
                URI.create("http://server-a02.server.lan:8180"),
                URI.create("http://server-a03.server.lan:8180")
        );
    }
}
