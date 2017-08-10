package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.ReverseProxy.Location;
import com.github.t1.nginx.NginxConfig;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import java.io.*;
import java.net.URI;
import java.util.stream.Stream;

import static com.github.t1.kubee.gateway.loadbalancer.LoadBalancerConfigAdapter.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NginxLoadBalancerGatewayTest {
    private static final String CONFIG_QA = ""
            + "http {\n"
            + "    upstream jolokia" + "qa-lb {\n"
            + "        server localhost:8180;\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name jolokia" + "qa;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://jolokiaqa-lb/jolokia;\n"
            + "            proxy_set_header Host      $host;\n"
            + "            proxy_set_header X-Real-IP $remote_addr;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker" + "qa1;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8180/;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker" + "qa2;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8280/;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    private static final String CONFIG_PROD = ""
            + "http {\n"
            + "    upstream jolokia-lb {\n"
            + "        least_conn;\n"
            + "\n"
            + "        server localhost:8380;\n"
            + "        server localhost:8480;\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name jolokia;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://jolokia-lb/jolokia;\n"
            + "            proxy_set_header Host      $host;\n"
            + "            proxy_set_header X-Real-IP $remote_addr;\n"
            + "        }\n"
            + "        location /service {\n"
            + "            proxy_pass http://dummy/service;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker1;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8380/;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker2;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8480/;\n"
            + "        }\n"
            + "    }\n"
            + "}\n";

    private static final Stage PROD = Stage.builder().name("PROD").count(2).build();
    private static final Stage QA = Stage.builder().name("QA").suffix("qa").build();

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private final LoadBalancerGateway gateway = new LoadBalancerGateway();
    private final LoadBalancerConfigAdapter nginxProd = mock(LoadBalancerConfigAdapter.class);
    private final LoadBalancerConfigAdapter nginxQa = mock(LoadBalancerConfigAdapter.class);


    @Before public void setUp() throws Exception {
        gateway.configAdapters.put(PROD.getName(), nginxProd);
        gateway.configAdapters.put(QA.getName(), nginxQa);
    }

    private LoadBalancerConfigAdapter adapter(Stage stage) {
        return gateway.configAdapters.get(stage.getName());
    }

    private void given(Stage stage, String contents) throws IOException {
        when(adapter(stage).read()).thenReturn(NginxConfig.readFrom(new StringReader(contents)));
    }

    private String updatedConfig(Stage stage) {
        ArgumentCaptor<NginxConfig> captor = ArgumentCaptor.forClass(NginxConfig.class);
        verify(adapter(stage)).update(captor.capture());
        return captor.getValue().toString();
    }

    private void verifyUpdated(Stage stage) { verify(adapter(stage)).update(any(NginxConfig.class)); }

    private void verifyNotUpdated(Stage stage) { verify(adapter(stage), never()).update(any(NginxConfig.class)); }

    @Test
    public void shouldGetProdLoadBalancers() throws Exception {
        given(PROD, CONFIG_PROD);

        Stream<LoadBalancer> loadBalancers = gateway.loadBalancers(PROD);

        assertThat(loadBalancers).containsExactly(
                LoadBalancer.builder().name("jolokia-lb").method("least_conn")
                            .server("localhost:8380").server("localhost:8480").build());
        verifyNotUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldGetQaLoadBalancers() throws Exception {
        given(QA, CONFIG_QA);

        Stream<LoadBalancer> loadBalancers = gateway.loadBalancers(QA);

        assertThat(loadBalancers).containsExactly(
                LoadBalancer.builder().name("jolokia" + "qa-lb")
                            .server("localhost:8180").build());
        verifyNotUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldGetQaReverseProxies() throws Exception {
        given(QA, CONFIG_QA);

        Stream<ReverseProxy> reverseProxies = gateway.reverseProxies(QA);

        assertThat(reverseProxies).containsExactly(
                ReverseProxy.builder().from(URI.create("http://jolokia" + "qa:80"))
                            .location(Location.from("/").to("http://jolokia" + "qa-lb/jolokia"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker" + "qa1:80"))
                            .location(Location.from("/").to("http://localhost:8180/"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker" + "qa2:80"))
                            .location(Location.from("/").to("http://localhost:8280/"))
                            .build());
        verifyNotUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldGetProdReverseProxies() throws Exception {
        given(PROD, CONFIG_PROD);

        Stream<ReverseProxy> reverseProxies = gateway.reverseProxies(PROD);

        assertThat(reverseProxies).containsExactly(
                ReverseProxy.builder().from(URI.create("http://jolokia:80"))
                            .location(Location.from("/").to("http://jolokia-lb/jolokia"))
                            .location(Location.from("/service").to("http://dummy/service"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker1:80"))
                            .location(Location.from("/").to("http://localhost:8380/"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker2:80"))
                            .location(Location.from("/").to("http://localhost:8480/"))
                            .build());
        verifyNotUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldRemoveFirstTargetFromLoadBalancer() throws Exception {
        given(PROD, CONFIG_PROD);

        gateway.from("jolokia", PROD).removeTarget(URI.create("http://worker1:80"));

        assertThat(updatedConfig(PROD))
                .isEqualTo(CONFIG_PROD.replace("        server localhost:8380;\n", ""));
        verifyNotUpdated(QA);
        verifyUpdated(PROD);
    }

    @Test
    public void shouldRemoveFinalTargetFromLoadBalancer() throws Exception {
        given(QA, CONFIG_QA);

        gateway.from("jolokia", QA).removeTarget(URI.create("http://worker" + "qa1:80"));

        assertThat(updatedConfig(QA))
                .isEqualTo(CONFIG_QA
                        .replace(""
                                + "    upstream jolokia" + "qa-lb {\n"
                                + "        server localhost:8180;\n"
                                + "    }\n"
                                + "\n", "")
                        .replace("http {\n", "http {\n    \n")
                        .replace(""
                                        + "    server {\n"
                                        + "        server_name jolokia" + "qa;\n"
                                        + "        listen 80;\n"
                                        + "        location / {\n"
                                        + "            proxy_pass http://jolokiaqa-lb/jolokia;\n"
                                        + "            proxy_set_header Host      $host;\n"
                                        + "            proxy_set_header X-Real-IP $remote_addr;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n",
                                ""));
        verifyUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldAddTargetToExistingLoadBalancer() throws Exception {
        given(QA, CONFIG_QA);

        gateway.to("jolokia", QA).addTarget(URI.create("http://worker" + "qa2:80"));

        assertThat(updatedConfig(QA))
                .isEqualTo(CONFIG_QA
                        .replace(""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8180;\n"
                                        + "    }\n",
                                ""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8180;\n"
                                        + "        server localhost:8280;\n"
                                        + "    }\n"));
        verifyUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldAddTargetToEmptyLoadBalancer() throws Exception {
        given(QA, CONFIG_QA.replace(""
                        + "    server {\n"
                        + "        server_name jolokia" + "qa;\n"
                        + "        listen 80;\n"
                        + "        location / {\n"
                        + "            proxy_pass http://jolokiaqa-lb/jolokia;\n"
                        + "        }\n"
                        + "    }\n"
                , ""
                        + "    server {\n"
                        + "        server_name jolokia" + "qa;\n"
                        + "        listen 80;\n"
                        + "    }\n"));

        gateway.to("jolokia", QA).addTarget(URI.create("http://worker" + "qa2:80"));

        assertThat(updatedConfig(QA))
                .isEqualTo(CONFIG_QA
                        .replace(""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8180;\n"
                                        + "    }\n",
                                ""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8180;\n"
                                        + "        server localhost:8280;\n"
                                        + "    }\n"));
        verifyUpdated(QA);
        verifyNotUpdated(PROD);
    }

    @Test
    public void shouldAddTargetToNewLoadBalancer() throws Exception {
        given(PROD, CONFIG_PROD);

        gateway.to("foo", PROD).addTarget(URI.create("http://worker1:80"));

        assertThat(updatedConfig(PROD))
                .isEqualTo(CONFIG_PROD
                        .replace(""
                                        + "http {\n",
                                ""
                                        + "http {\n"
                                        + "    upstream foo-lb {\n"
                                        + "        least_conn;\n"
                                        + "\n"
                                        + "        server localhost:8380;\n"
                                        + "    }\n"
                                        + "\n")
                        .replace(""
                                        + "    server {\n"
                                        + "        server_name jolokia;\n",
                                ""
                                        + "    server {\n"
                                        + "        server_name foo;\n"
                                        + "        listen 80;\n"
                                        + "        location / {\n"
                                        + "            proxy_pass http://foo-lb/foo/;\n"
                                        + "            proxy_set_header Host      $host;\n"
                                        + "            proxy_set_header X-Real-IP $remote_addr;\n"
                                        + "        }\n"
                                        + "    }\n"
                                        + "\n"
                                        + "    server {\n"
                                        + "        server_name jolokia;\n"));
        verifyUpdated(PROD);
        verifyNotUpdated(QA);
    }

    @Test
    public void shouldCreateDefaultProdConfigAdapter() throws Exception {
        gateway.configAdapters.clear();

        gateway.loadBalancers(PROD);

        assertThat(gateway.configAdapters).containsKey(PROD.getName());
        LoadBalancerConfigAdapter adapter = gateway.configAdapters.get(PROD.getName());
        assertThat(adapter.configPath).hasToString("/usr/local/etc/nginx/nginx.conf");
        assertThat(adapter.reload).isInstanceOf(ServiceReload.class);
        assertThat(((ServiceReload) adapter.reload).adapter.port).isEqualTo(NginxReloadService.DEFAULT_PORT);
    }

    @Test
    public void shouldCreateDefaultQaConfigAdapter() throws Exception {
        gateway.configAdapters.clear();

        gateway.nginx(QA);

        assertThat(gateway.configAdapters).containsKey(QA.getName());
        LoadBalancerConfigAdapter adapter = gateway.configAdapters.get(QA.getName());
        assertThat(adapter.configPath).hasToString("/usr/local/etc/nginx/nginx" + "qa.conf");
        assertThat(adapter.reload).isInstanceOf(ServiceReload.class);
        assertThat(((ServiceReload) adapter.reload).adapter.port).isEqualTo(NginxReloadService.DEFAULT_PORT);
    }

    @Test
    public void shouldCreateConfigAdapterWithPort() throws Exception {
        Stage stage = Stage.builder().name("DUMMY").suffix("dummy").loadBalancerConfig(
                ServiceReload.RELOAD_SERVICE_PORT, "1234").build();
        gateway.configAdapters.clear();

        gateway.nginx(stage);

        assertThat(gateway.configAdapters).containsKey(stage.getName());
        LoadBalancerConfigAdapter adapter = gateway.configAdapters.get(stage.getName());
        assertThat(adapter.configPath).hasToString("/usr/local/etc/nginx/nginx" + "dummy.conf");
        assertThat(adapter.reload).isInstanceOf(ServiceReload.class);
        assertThat(((ServiceReload) adapter.reload).adapter.port).isEqualTo(1234);
    }

    @Test
    public void shouldCreateConfigAdapterWithUnknownReloadMode() throws Exception {
        gateway.configAdapters.clear();
        Stage stage = Stage.builder().name("DUMMY").loadBalancerConfig(RELOAD_MODE, "foo").build();

        assertThatThrownBy(() -> gateway.nginx(stage))
                .hasMessage("unknown reload mode: foo");
    }

    @Test
    public void shouldCreateConfigAdapterWithDirectReload() throws Exception {
        Stage stage = Stage.builder().name("DUMMY").loadBalancerConfig(RELOAD_MODE, "direct").build();
        gateway.configAdapters.clear();

        gateway.nginx(stage);

        assertThat(gateway.configAdapters).containsKey(stage.getName());
        LoadBalancerConfigAdapter adapter = gateway.configAdapters.get(stage.getName());
        assertThat(adapter.configPath).hasToString("/usr/local/etc/nginx/nginx.conf");
        assertThat(adapter.reload).isInstanceOf(LoadBalancerConfigAdapter.DirectReload.class);
    }

    @Test
    public void shouldCreateConfigAdapterWithScriptReload() throws Exception {
        Stage stage = Stage.builder().name("DUMMY").loadBalancerConfig(RELOAD_MODE, "set-user-id-script").build();
        gateway.configAdapters.clear();

        gateway.nginx(stage);

        assertThat(gateway.configAdapters).containsKey(stage.getName());
        LoadBalancerConfigAdapter adapter = gateway.configAdapters.get(stage.getName());
        assertThat(adapter.configPath).hasToString("/usr/local/etc/nginx/nginx.conf");
        assertThat(adapter.reload).isInstanceOf(LoadBalancerConfigAdapter.SetUserIdScriptReload.class);
    }
}
