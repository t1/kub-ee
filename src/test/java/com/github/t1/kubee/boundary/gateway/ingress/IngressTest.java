package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.boundary.gateway.ingress.Ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
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

import static com.github.t1.kubee.boundary.gateway.ingress.Ingress.NGINX_ETC;
import static com.github.t1.kubee.tools.Tools.toEndpoint;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

@SuppressWarnings("OptionalGetWithoutIsPresent") class IngressTest {
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


    private Ingress whenIngress() {
        return Ingress.ingress(STAGE);
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

        Stream<LoadBalancer> loadBalancers = whenIngress().loadBalancers();

        assertThat(loadBalancers)
            .extracting(LoadBalancer::name, LoadBalancer::method, loadBalancer -> loadBalancer.endpoints().collect(toList()))
            .containsExactly(tuple("dummy-app-lb", "least_conn", singletonList(toEndpoint(WORKER01))));
    }

    @Test void shouldGetReverseProxies() {
        givenNginx(WORKER01, WORKER02);

        Stream<ReverseProxy> reverseProxies = whenIngress().reverseProxies();

        assertThat(reverseProxies)
            .extracting(ReverseProxy::name, ReverseProxy::listen, ReverseProxy::getPort)
            .containsExactly(
                tuple("worker-prod1", 8080, 10001),
                tuple("worker-prod2", 8080, 10002)
            );
    }

    @Test void shouldFailToAddUnknownNodeToLoadBalancer() {
        givenNginx(WORKER01);

        Throwable throwable = catchThrowable(() -> whenIngress().addToLoadBalancerFor("dummy-app", NODE2));

        assertThat(throwable).isInstanceOf(IllegalStateException.class).hasMessage("no reverse proxy found for worker-prod2 in [worker-prod1]");
        verifyNotReloaded();
    }

    @Test void shouldAddNodeToLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        whenIngress().addToLoadBalancerFor("dummy-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyReloaded();
    }

    @Test void shouldIgnoreToRemoveNodeFromUnknownLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        whenIngress().removeFromLoadBalancer("unknown-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyNotReloaded();
    }

    @Test void shouldRemoveNodeFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        whenIngress().removeFromLoadBalancer("dummy-app", NODE2);

        assertThat(actualNginxConfig()).isEqualTo(removeNode(nginxConfig(WORKER01, WORKER02), NODE2));
        verifyReloaded();
    }

    @Test void shouldRestoreOldConfigWhenReloadFailsAfterNodeRemovedFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);
        ReloadMock.error = "dummy-error";

        Throwable throwable = catchThrowable(() -> whenIngress().removeFromLoadBalancer("dummy-app", NODE1));

        assertThat(throwable).hasMessage("failed to reload load balancer: dummy-error");
        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyReloaded();
    }
}
