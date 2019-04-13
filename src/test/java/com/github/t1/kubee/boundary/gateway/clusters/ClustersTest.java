package com.github.t1.kubee.boundary.gateway.clusters;

import com.github.t1.kubee.entity.Cluster;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.NODE1;
import static com.github.t1.kubee.TestData.NODE2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;

class ClustersTest {
    private static final String YAML = "" +
        ":index-length: 2\n" +
        ":slot:0:\n" +
        "  http: 8080\n" +
        "  https: 443\n" +
        "worker:0:\n" +
        "  PROD:\n" +
        "    suffix: -prod\n" +
        "    count: 2\n" +
        "    index-length: 0\n" +
        "    load-balancer:\n" +
        "      reload: custom\n" +
        "      class: com.github.t1.kubee.boundary.gateway.ingress.ReloadMock\n";
    private static final String UNBALANCED_YAML = YAML +
        "    status:\n" +
        "      1:app-name: unbalanced\n";

    private final Clusters clusters = new Clusters();

    @TempDir Path tmp;

    private final String origUserDir = System.getProperty("user.dir");
    private Path configFile;

    @AfterEach void tearDown() { System.setProperty("user.dir", origUserDir); }

    @SneakyThrows(IOException.class)
    private void givenClusterConfig(String yaml) {
        this.configFile = tmp.resolve("cluster-config.yaml");
        System.setProperty("user.dir", tmp.toString());
        Files.write(configFile, yaml.getBytes());
    }

    @Test void shouldReadClustersFromUserDir() {
        givenClusterConfig(YAML);

        Stream<Cluster> stream = clusters.stream();

        assertThat(stream).containsExactly(CLUSTER);
    }

    @Test void shouldUnbalance() {
        givenClusterConfig(YAML);

        clusters.unbalance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldNotUnbalanceWhenAlreadyUnbalanced() {
        givenClusterConfig(UNBALANCED_YAML);

        clusters.unbalance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldBalance() {
        givenClusterConfig(UNBALANCED_YAML);

        clusters.balance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(YAML);
    }

    @Test void shouldBalance1of2nodes() {
        givenClusterConfig(UNBALANCED_YAML + "      2:app-name: unbalanced\n");

        clusters.balance(NODE2, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldBalance1of2apps() {
        givenClusterConfig(UNBALANCED_YAML + "      1:app2-name: unbalanced\n");

        clusters.balance(NODE1, "app2-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldNotBalanceWhenAlreadyBalanced() {
        givenClusterConfig(YAML);

        clusters.balance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(YAML);
    }
}
