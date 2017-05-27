package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import org.junit.Test;

import java.net.URI;

import static com.github.t1.metadeployer.model.Stage.*;
import static org.assertj.core.api.Assertions.*;

public class ClusterTest {
    private static final Cluster[] CLUSTERS = {
            Cluster.builder().name("my.boss").port(8080)
                   .stage(Stage.builder().name("DEV").prefix("").suffix(".dev").count(5).indexLength(2).build())
                   .stage(Stage.builder().name("QA").prefix("qa.").suffix("").count(1).indexLength(0).build())
                   .stage(Stage.builder().name("PROD").prefix("").suffix("").count(3).indexLength(2).build())
                    .build(),
            Cluster.builder().name("other.boss").port(80)
                   .stage(Stage.builder().name("DEV").prefix("").suffix("").count(2).indexLength(0).build())
                    .build(),
            Cluster.builder().name("third.boss").port(80)
                   .stage(NULL_STAGE)
                    .build()
    };

    @Test
    public void shouldReadYamlConfig() throws Exception {
        ClusterConfig clusterConfig = new ClusterConfig();

        clusterConfig.readFrom(ClusterTest.class.getResourceAsStream("cluster-config.yaml"));

        assertThat(clusterConfig.clusters()).containsOnly(CLUSTERS);
    }

    @Test
    public void shouldProduceAllUrls() throws Exception {
        assertThat(CLUSTERS[0].allUris()).containsOnly(
                URI.create("http://my.boss.dev01:8080"),
                URI.create("http://my.boss.dev02:8080"),
                URI.create("http://my.boss.dev03:8080"),
                URI.create("http://my.boss.dev04:8080"),
                URI.create("http://my.boss.dev05:8080"),
                URI.create("http://qa.my.boss:8080"),
                URI.create("http://my.boss01:8080"),
                URI.create("http://my.boss02:8080"),
                URI.create("http://my.boss03:8080")
        );
    }
}
