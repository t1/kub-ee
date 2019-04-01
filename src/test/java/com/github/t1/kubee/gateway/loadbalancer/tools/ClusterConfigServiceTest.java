package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.tools.cli.ProcessInvoker;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClusterConfigServiceTest {
    private static final HostPort WORKER01 = HostPort.valueOf("worker01:10001");
    private static final HostPort WORKER02 = HostPort.valueOf("worker02:10002");
    private static final HostPort WORKER03 = HostPort.valueOf("worker03:10003");

    @TempDir Path tmp;
    private Path nginxConfigPath;
    private Path clusterConfigPath;
    private final Path dockerComposeConfigPath = Paths.get("./test/docker/docker-compose.yaml");

    private final ProcessInvoker proc = mock(ProcessInvoker.class);

    private ClusterConfigService service;

    @BeforeEach
    void setUp() {
        nginxConfigPath = tmp.resolve("nginx.conf");
        clusterConfigPath = tmp.resolve("cluster-config.yaml");
        service = new ClusterConfigService()
            .setProc(proc)
            .setDockerComposeConfigPath(dockerComposeConfigPath)
            .setClusterConfigPath(clusterConfigPath)
            .setNginxConfigPath(nginxConfigPath)
            .setContinues(false);
    }

    @SneakyThrows(IOException.class)
    private void givenClusterConfig(int count) {
        String clusterConfig = "" +
            ":index-length: 2\n" +
            ":slot:0:\n" +
            "  http: 8080\n" +
            "  https: 8443\n" +
            "worker:0:\n" +
            "  PROD:\n" +
            "    count: " + count + "\n" +
            "    load-balancer:\n" +
            "      reload: direct\n" +
            "#      reload: docker-kill-hup\n" +
            "#      host: docker_lb_1\n";
        Files.write(clusterConfigPath, singletonList(clusterConfig));
    }

    @SneakyThrows(IOException.class)
    private void givenNginx(HostPort... workers) {
        Files.write(nginxConfigPath, singletonList(nginxConfig(workers).toString()));
    }

    private NginxConfig nginxConfig(HostPort... workers) {
        NginxConfig nginxConfig = NginxConfig.create()
            .addUpstream(NginxUpstream.named("worker_nodes").setMethod("least_conn").setHostPorts(asList(workers)))
            .addServer(NginxServer.named("~^(?<app>.+).kub-ee$").setListen(8080)
                .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://worker_nodes/$app"))
                    .setAfter("proxy_set_header Host      $host;\n" +
                        "            proxy_set_header X-Real-IP $remote_addr;")));
        for (int i = 0; i < workers.length; i++) {
            String upstreamName = "worker0" + (i + 1);
            nginxConfig
                .addUpstream(NginxUpstream.named(upstreamName).setMethod("least_conn").addHostPort(workers[i]))
                .addServer(NginxServer.named(upstreamName).setListen(8080)
                    .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://" + upstreamName + "/"))
                        .setAfter("proxy_set_header Host      $host;\n" +
                            "            proxy_set_header X-Real-IP $remote_addr;")));
        }
        return nginxConfig;
    }

    private void givenDocker(HostPort... workers) {
        List<String> containerIds = new ArrayList<>();
        for (int i = 0; i < workers.length; i++) {
            String containerId = UUID.randomUUID().toString();
            containerIds.add(containerId);
            given(proc.invoke("docker", "ps", "--format", "{{.Ports}}\t{{.Names}}", "--filter", "id=" + containerId, "--filter", "publish=8080"))
                .willReturn("0.0.0.0:" + workers[i].getPort() + "->8080/tcp\tdocker_worker_" + (i + 1));
        }
        given(proc.invoke(dockerComposeConfigPath.getParent(), "docker-compose", "ps", "-q", "worker"))
            .willReturn(join("\n", containerIds));
    }

    private NginxConfig actualNginxConfig() { return NginxConfig.readFrom(nginxConfigPath.toUri()); }


    @Test void shouldRunEmpty() {
        givenClusterConfig(1);
        givenDocker(WORKER01);
        givenNginx(WORKER01);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01));
    }

    @Test void shouldUpdatePortOfWorker01() {
        givenClusterConfig(1);
        HostPort actualWorker1 = WORKER01.withPort(20000);
        givenDocker(actualWorker1);
        givenNginx(WORKER01);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(actualWorker1));
    }

    @Test void shouldUpdatePortOfSecondWorkerOf3() {
        givenClusterConfig(3);
        HostPort actualWorker2 = WORKER02.withPort(20000);
        givenDocker(WORKER01, actualWorker2, WORKER03);
        givenNginx(WORKER01, WORKER02, WORKER03);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, actualWorker2, WORKER03));
    }

    @Test void shouldAddUpstream() {
        givenClusterConfig(2);
        givenDocker(WORKER01, WORKER02);
        givenNginx(WORKER01);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
    }

    @Test void shouldRemoveUpstream() {
        givenClusterConfig(1);
        givenDocker(WORKER01);
        givenNginx(WORKER01, WORKER02);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01));
    }

    @Test void shouldRemoveTwoUpstreams() {
        givenClusterConfig(1);
        givenDocker(WORKER01);
        givenNginx(WORKER01, WORKER02, WORKER03);

        service.run();

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01));
    }

    // FIXME multiple stages
    // FIXME multiple clusters
}
