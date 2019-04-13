package com.github.t1.kubee.boundary.cli.config;

import com.github.t1.kubee.tools.ContainersFixture;
import com.github.t1.kubee.tools.SystemOutExtension;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.t1.kubee.tools.ContainersFixture.WORKER1;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterConfigServiceTest {
    private static final String USAGE = "Usage:\n" +
        "    `--once`: to run only once and exit. Otherwise: loop until stopped.\n" +
        "    `--cluster-config=<path>`: with the <path> to the `cluster-config.yaml`\n" +
        "    `--docker-compose-dir=<path>`: with the <path> to the directory containing the `docker-compose.yaml`";
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
    @RegisterExtension SystemOutExtension system = new SystemOutExtension();

    @BeforeEach void before() {
        containers.setDockerComposeDir(tmp).setPort(8080);
        clusterConfig = tmp.resolve("cluster-config.yaml");
        writeClusterConfig(CLUSTER_CONFIG);
    }

    @SneakyThrows(IOException.class)
    private void writeClusterConfig(String clusterConfig) {
        Files.write(this.clusterConfig, clusterConfig.getBytes());
    }

    private void assertResult(Integer status, String out, String err) {
        assertThat(system.status).isEqualTo(status);
        assertThat(system.out().trim()).isEqualTo(out);
        if (err != null)
            assertThat(system.err().trim()).isEqualTo(err);
    }

    @Test void shouldFailToRunWithoutArgs() {
        ClusterConfigService.main();

        assertResult(1, "", USAGE);
    }

    @Test void shouldRunOnce() {
        containers.givenEndpoints(WORKER1);

        ClusterConfigService.main("--once", "--cluster-config=" + clusterConfig, "--docker-compose-dir=" + containers.getDockerComposeDir());

        assertResult(0, "" +
                "Start ClusterConfigService for " + clusterConfig + "\n" +
                "recondition start\n" +
                "recondition done\n" +
                "end loop",
            null);
    }

    @Test void shouldRunUntilStop() {
        containers.givenEndpoints(WORKER1);
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

        assertResult(null, "" +
                "recondition start\n" +
                "recondition done\n" +
                "end loop",
            null);
    }
}
