package com.github.t1.kubee.boundary.gateway.clusters;

import com.github.t1.kubee.entity.Cluster;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.NODE1;
import static com.github.t1.kubee.TestData.NODE2;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.contentOf;

class ClusterStoreTest {
    private static final String YAML = "" +
        ":index-length: 2\n" +
        ":slot:0:\n" +
        "  http: 8080\n" +
        "  https: 8443\n" +
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

    private ClusterStore clusterStore;

    @TempDir Path tmp;

    private Path configFile;

    @BeforeEach void setUp() {
        this.configFile = tmp.resolve("cluster-config.yaml");
        this.clusterStore = new ClusterStore(configFile);
    }

    @SneakyThrows(IOException.class)
    private void givenClusterConfig(String yaml) {
        Files.write(configFile, yaml.getBytes());
    }

    @Test void shouldReadClustersFromConfigPath() {
        givenClusterConfig(YAML);

        Stream<Cluster> stream = clusterStore.clusters();

        assertThat(stream).containsExactly(CLUSTER);
    }

    @Test void shouldFailToReadMissingClusters() {
        Throwable throwable = catchThrowable(clusterStore::clusters);

        assertThat(throwable).hasMessage("can't read cluster config file: " + configFile);
    }

    @Test void shouldUnbalance() {
        givenClusterConfig(YAML);

        clusterStore.unbalance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldFailToWriteConfigFile() throws IOException {
        givenClusterConfig(YAML);
        Files.setPosixFilePermissions(configFile, singleton(OWNER_READ)); // not write

        Throwable throwable = catchThrowable(() -> clusterStore.unbalance(NODE1, "app-name"));

        assertThat(throwable).hasMessage("can't write cluster config file: " + configFile);
    }

    @Test void shouldNotUnbalanceWhenAlreadyUnbalanced() {
        givenClusterConfig(UNBALANCED_YAML);

        clusterStore.unbalance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldBalance() {
        givenClusterConfig(UNBALANCED_YAML);

        clusterStore.balance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(YAML);
    }

    @Test void shouldBalance1of2nodes() {
        givenClusterConfig(UNBALANCED_YAML + "      2:app-name: unbalanced\n");

        clusterStore.balance(NODE2, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldBalance1of2apps() {
        givenClusterConfig(UNBALANCED_YAML + "      1:app2-name: unbalanced\n");

        clusterStore.balance(NODE1, "app2-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(UNBALANCED_YAML);
    }

    @Test void shouldNotBalanceWhenAlreadyBalanced() {
        givenClusterConfig(YAML);

        clusterStore.balance(NODE1, "app-name");

        assertThat(contentOf(configFile.toFile())).isEqualTo(YAML);
    }
}
