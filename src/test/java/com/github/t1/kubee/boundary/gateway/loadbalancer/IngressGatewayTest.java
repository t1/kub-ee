package com.github.t1.kubee.boundary.gateway.loadbalancer;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.LoadBalancer;
import com.github.t1.kubee.entity.ReverseProxy;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.http.ProblemDetail;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.stream.Stream;

import static com.github.t1.kubee.boundary.gateway.loadbalancer.IngressGateway.NGINX_ETC;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

@SuppressWarnings("OptionalGetWithoutIsPresent") class IngressGatewayTest {
    private static final HostPort WORKER01 = HostPort.valueOf("worker-prod1:10001");
    private static final HostPort WORKER02 = HostPort.valueOf("worker-prod2:10002");
    private static final String PROXY_SETTINGS = "proxy_set_header Host      $host;\n" +
        "            proxy_set_header X-Real-IP $remote_addr;";

    private static final Stage STAGE = Stage.builder().name("PROD").suffix("-prod").count(2)
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();
    private static final Slot SLOT = Slot.builder().name("0").http(8080).build();
    private static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).build();
    private static final ClusterNode NODE1 = new ClusterNode(CLUSTER, STAGE, 1);
    private static final ClusterNode NODE2 = new ClusterNode(CLUSTER, STAGE, 2);

    private final IngressGateway gateway = new IngressGateway();

    private Path origConfigPath;
    @TempDir Path nginxEtc;
    private Path configPath;

    @BeforeEach void setUp() {
        origConfigPath = NGINX_ETC;
        NGINX_ETC = nginxEtc;
        configPath = nginxEtc.resolve("nginx-prod.conf");
    }

    private void givenNginx(HostPort... workers) {
        givenNginx(nginxConfig(workers));
    }

    @SneakyThrows(IOException.class)
    private void givenNginx(NginxConfig nginxConfig) {
        Files.write(configPath, singletonList(nginxConfig.toString()));
    }

    private NginxConfig nginxConfig(HostPort... workers) {
        NginxConfig nginxConfig = NginxConfig.create()
            .addUpstream(NginxUpstream.named("dummy-app-lb").setMethod("least_conn").setHostPorts(new ArrayList<>(asList(workers))))
            .addServer(NginxServer.named("dummy-app").setListen(80)
                .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://dummy-app-lb/dummy-app")).setAfter(PROXY_SETTINGS)));
        addReverseProxy(nginxConfig, workers);
        return nginxConfig;
    }

    private void addReverseProxy(NginxConfig nginxConfig, HostPort... workers) {
        for (HostPort worker : workers) {
            String upstreamName = worker.getHost();
            nginxConfig
                .addUpstream(NginxUpstream.named(upstreamName).setMethod("least_conn").addHostPort(worker))
                .addServer(NginxServer.named(upstreamName).setListen(SLOT.getHttp())
                    .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://" + upstreamName + "/"))
                        .setAfter("proxy_set_header Host      $host;\n" +
                            "            proxy_set_header X-Real-IP $remote_addr;")));
        }
    }

    private NginxConfig removeNode(NginxConfig config, ClusterNode node) {
        config.upstream("dummy-app-lb").get().removeHost(node.host());
        return config;
    }

    private NginxConfig actualNginxConfig() { return NginxConfig.readFrom(configPath.toUri()); }

    private void verifyReloaded() { assertThat(ReloadMock.calls).isEqualTo(1); }

    private void verifyNotReloaded() { assertThat(ReloadMock.calls).isEqualTo(0); }

    @AfterEach
    void tearDown() {
        NGINX_ETC = origConfigPath;
        ReloadMock.reset();
    }

    @Test void shouldGetLoadBalancers() {
        givenNginx(WORKER01);

        Stream<LoadBalancer> loadBalancers = gateway.loadBalancers(STAGE);

        assertThat(loadBalancers).containsExactly(
            LoadBalancer.builder().name("dummy-app-lb").method("least_conn").server("worker-prod1:10001").build()
        );
    }

    @Test void shouldGetReverseProxies() {
        givenNginx(WORKER01, WORKER02);

        Stream<ReverseProxy> reverseProxies = gateway.reverseProxies(STAGE);

        assertThat(reverseProxies).containsExactly(
            ReverseProxy.builder().from(URI.create("http://worker-prod1:8080")).to(10001).build(),
            ReverseProxy.builder().from(URI.create("http://worker-prod2:8080")).to(10002).build()
        );
    }

    @Test void shouldFailToAddUnknownNodeToLoadBalancer() {
        givenNginx(WORKER01);

        Throwable throwable = catchThrowable(() -> gateway.add("dummy-app", NODE2));

        assertThat(throwable).isInstanceOf(IllegalStateException.class).hasMessage("no reverse proxy found for worker-prod2 in [worker-prod1]");
        verifyNotReloaded();
    }

    @Test void shouldAddNodeToLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        gateway.add("dummy-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyReloaded();
    }

    @Test void shouldIgnoreToRemoveNodeFromUnknownLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        gateway.remove("unknown-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyNotReloaded();
    }

    @Test void shouldRemoveNodeFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        gateway.remove("dummy-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(removeNode(nginxConfig(WORKER01, WORKER02), NODE2));
        verifyReloaded();
    }

    @Test void shouldRestoreOldConfigWhenReloadFailsAfterNodeRemovedFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);
        ReloadMock.error = "dummy-error";

        WebApplicationApplicationException throwable = catchThrowableOfType(() ->
                gateway.remove("dummy-app", NODE1),
            WebApplicationApplicationException.class);

        assertThat(throwable.getDetail()).extracting(ProblemDetail::getStatus, ProblemDetail::getDetail)
            .containsExactly(INTERNAL_SERVER_ERROR, "failed to reload load balancer: dummy-error");
        assertThat(actualNginxConfig()).isEqualTo(removeNode(nginxConfig(WORKER01, WORKER02), NODE1));
        verifyReloaded();
    }
}
