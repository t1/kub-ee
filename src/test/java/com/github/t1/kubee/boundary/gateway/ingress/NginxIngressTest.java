package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.TestData;
import com.github.t1.kubee.entity.ClusterNode;
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

import static com.github.t1.kubee.TestData.PROD;
import static com.github.t1.kubee.TestData.PROD01;
import static com.github.t1.kubee.TestData.PROD02;
import static com.github.t1.kubee.TestData.PROXY_SETTINGS;
import static com.github.t1.kubee.TestData.WORKER01;
import static com.github.t1.kubee.TestData.WORKER02;
import static com.github.t1.kubee.boundary.gateway.ingress.IngressFactory.ingress;
import static com.github.t1.kubee.boundary.gateway.ingress.NginxIngress.NGINX_ETC;
import static com.github.t1.kubee.boundary.gateway.ingress.NginxIngress.toEndpoint;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.Assertions.tuple;

class NginxIngressTest {
    private Path origConfigPath;
    @TempDir Path nginxEtc;
    private Path configPath;

    @BeforeEach void setUp() {
        origConfigPath = NGINX_ETC;
        NGINX_ETC = nginxEtc;
        configPath = nginxEtc.resolve("nginx.conf");
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

    private NginxConfig addReverseProxy(NginxConfig nginxConfig, HostPort... workers) {
        for (HostPort worker : workers) {
            String upstreamName = worker.getHost();
            nginxConfig
                .addUpstream(NginxUpstream.named(upstreamName).setMethod("least_conn").addHostPort(worker))
                .addServer(NginxServer.named(upstreamName).setListen(TestData.SLOT_0.getHttp())
                    .addLocation(NginxServerLocation.named("/").setProxyPass(URI.create("http://" + upstreamName + "/"))
                        .setAfter("proxy_set_header Host      $host;\n" +
                            "            proxy_set_header X-Real-IP $remote_addr;")));
        }
        return nginxConfig;
    }

    private NginxConfig removeNode(NginxConfig config, @SuppressWarnings("SameParameterValue") ClusterNode node) {
        config.upstream("dummy-app-lb").orElseThrow(() -> new RuntimeException("not found")).removeHost(node.host());
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

        Stream<? extends LoadBalancer> loadBalancers = ingress(PROD).loadBalancers();

        assertThat(loadBalancers)
            .extracting(LoadBalancer::applicationName, LoadBalancer::method, loadBalancer -> loadBalancer.endpoints().collect(toList()))
            .containsExactly(tuple("dummy-app", "least_conn", singletonList(toEndpoint(WORKER01))));
    }

    @Test void shouldGetReverseProxies() {
        givenNginx(WORKER01, WORKER02);

        Stream<ReverseProxy> reverseProxies = ingress(PROD).reverseProxies();

        assertThat(reverseProxies)
            .extracting(ReverseProxy::name, ReverseProxy::listen, ReverseProxy::getPort)
            .containsExactly(
                tuple("worker01", 8080, 10001),
                tuple("worker02", 8080, 10002)
            );
    }

    @Test void shouldFailToAddUnknownNodeToLoadBalancer() {
        givenNginx(WORKER01);

        Throwable throwable = catchThrowable(() -> ingress(PROD).addToLoadBalancer("dummy-app", PROD02));

        assertThat(throwable).isInstanceOf(IllegalStateException.class).hasMessage("no reverse proxy found for worker02 in [worker01]");
        verifyNotReloaded();
    }

    @Test void shouldAddNodeToLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        ingress(PROD).addToLoadBalancer("dummy-app", PROD02);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyReloaded();
    }

    @Test void shouldIgnoreToRemoveNodeFromUnknownLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        ingress(PROD).removeFromLoadBalancer("unknown-app", PROD02);

        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyNotReloaded();
    }

    @Test void shouldRemoveNodeFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);

        ingress(PROD).removeFromLoadBalancer("dummy-app", PROD02);

        assertThat(actualNginxConfig()).isEqualTo(removeNode(nginxConfig(WORKER01, WORKER02), PROD02));
        verifyReloaded();
    }

    @Test void shouldRemoveLastNodeFromLoadBalancer() {
        givenNginx(WORKER01);

        ingress(PROD).removeFromLoadBalancer("dummy-app", PROD01);

        assertThat(actualNginxConfig()).isEqualTo(addReverseProxy(NginxConfig.create(), WORKER01));
        verifyReloaded();
    }

    @Test void shouldRestoreOldConfigWhenReloadFailsAfterNodeRemovedFromLoadBalancer() {
        givenNginx(WORKER01, WORKER02);
        ReloadMock.error = "dummy-error";

        Throwable throwable = catchThrowable(() -> ingress(PROD).removeFromLoadBalancer("dummy-app", PROD01));

        assertThat(throwable).hasMessage("failed to reload load balancer: dummy-error");
        assertThat(actualNginxConfig()).isEqualTo(nginxConfig(WORKER01, WORKER02));
        verifyReloaded();
    }
}
