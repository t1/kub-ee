package com.github.t1.kubee.boundary.cli.config;

import com.github.t1.kubee.tools.ContainersFixture;
import com.github.t1.testtools.MockLogger;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.t1.kubee.TestData.PROD_ENDPOINT1;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.SEVERE;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterConfigServiceTest {
    private static final String CLUSTER_CONFIG = "" +
        ":index-length: 2\n" +
        ":slot:0:\n" +
        "  http: 8080\n" +
        "  https: 8443\n" +
        "worker:0:\n" +
        "  PROD:\n" +
        "    count: 1\n" +
        "    load-balancer:\n" +
        "      reload: direct\n";

    @TempDir Path tmp;
    private Path clusterConfig;

    @RegisterExtension ContainersFixture containers = new ContainersFixture();
    private MockLogger mockLogger = new MockLogger();
    private Integer exitCode;

    @BeforeEach void before() {
        ClusterConfigService.exit = code -> this.exitCode = code;
        ClusterConfigService.log = mockLogger;
        containers.setDockerComposeDir(tmp);
        clusterConfig = tmp.resolve("cluster-config.yaml");
        writeClusterConfig();
    }

    @SneakyThrows(IOException.class)
    private void writeClusterConfig() {
        Files.write(this.clusterConfig, CLUSTER_CONFIG.getBytes());
    }

    @Test void shouldFailToRunWithoutArgs() {
        ClusterConfigService.main();

        assertThat(exitCode).isEqualTo(1);
        assertThat(mockLogger.getMessages(SEVERE)).isEqualTo("Usage:\n" +
            "    `--once`: to run only once and exit. Otherwise: loop until stopped.\n" +
            "    `--cluster-config=<path>`: with the <path> to the `cluster-config.yaml`\n" +
            "    `--docker-compose-dir=<path>`: with the <path> to the directory containing the `docker-compose.yaml`\n");
    }

    @Test void shouldRunOnce() {
        containers.given(PROD_ENDPOINT1);

        ClusterConfigService.main("--once", "--cluster-config=" + clusterConfig, "--docker-compose-dir=" + containers.getDockerComposeDir());

        assertThat(exitCode).isEqualTo(0);
        assertThat(mockLogger.getMessages(INFO)).isEqualTo("" +
            "Start ClusterConfigService for " + clusterConfig + "\n" +
            "recondition start\n" +
            "recondition done\n" +
            "end loop");
    }

    @Test void shouldRunUntilStop() {
        containers.given(PROD_ENDPOINT1);
        ClusterConfigService service = new ClusterConfigService(clusterConfig, containers.getDockerComposeDir(), true);

        new Thread(() -> {
            try {
                Thread.sleep(100);
                service.stop();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, "stopper").start();

        service.loop();

        assertThat(exitCode).isEqualTo(null);
        assertThat(mockLogger.getMessages(INFO)).isEqualTo("" +
            "recondition start\n" +
            "recondition done\n" +
            "end loop");
    }

    // TODO make the DeployerGateway work from the CLI, too (JAX-RS client is not on the classpath)
}
