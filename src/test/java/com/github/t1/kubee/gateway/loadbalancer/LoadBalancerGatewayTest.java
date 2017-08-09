package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.ReverseProxy.Location;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;

import static java.nio.file.Files.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class LoadBalancerGatewayTest {
    private static final String CONFIG = ""
            + "http {\n"
            + "    upstream jolokia-lb {\n"
            + "        least_conn;\n"
            + "\n"
            + "        server localhost:8180;\n"
            + "        server localhost:8280;\n"
            + "    }\n"
            + "\n"
            + "    upstream jolokia" + "qa-lb {\n"
            + "        server localhost:8380;\n"
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
            + "        server_name worker1;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8180/;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker2;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8280/;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker" + "qa1;\n"
            + "        listen 80;\n"
            + "        location / {\n"
            + "            proxy_pass http://localhost:8380/;\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    server {\n"
            + "        server_name worker" + "qa2;\n"
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

    @Before public void setUp() throws Exception {
        //noinspection unchecked
        gateway.reloadMode = mock(Callable.class);
    }

    private void givenConfig(String contents) throws IOException {
        gateway.nginxConfigPath = folder.newFile("test.config").toPath();
        write(gateway.nginxConfigPath, contents.getBytes("UTF-8"));
    }

    @Test
    public void shouldGetLoadBalancers() throws Exception {
        givenConfig(CONFIG);

        List<LoadBalancer> loadBalancers = gateway.getLoadBalancers();

        assertThat(loadBalancers).containsExactly(
                LoadBalancer.builder().name("jolokia-lb").method("least_conn")
                            .server("localhost:8180").server("localhost:8280").build(),
                LoadBalancer.builder().name("jolokia" + "qa-lb")
                            .server("localhost:8380").build());
        verifyZeroInteractions(gateway.reloadMode);
    }

    @Test
    public void shouldGetReverseProxies() throws Exception {
        givenConfig(CONFIG);

        List<ReverseProxy> reverseProxies = gateway.getReverseProxies();

        assertThat(reverseProxies).containsExactly(
                ReverseProxy.builder().from(URI.create("http://jolokia:80"))
                            .location(Location.from("/").to("http://jolokia-lb/jolokia"))
                            .location(Location.from("/service").to("http://dummy/service"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://jolokia" + "qa:80"))
                            .location(Location.from("/").to("http://jolokia" + "qa-lb/jolokia"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker1:80"))
                            .location(Location.from("/").to("http://localhost:8180/"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker2:80"))
                            .location(Location.from("/").to("http://localhost:8280/"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker" + "qa1:80"))
                            .location(Location.from("/").to("http://localhost:8380/"))
                            .build(),
                ReverseProxy.builder().from(URI.create("http://worker" + "qa2:80"))
                            .location(Location.from("/").to("http://localhost:8480/"))
                            .build());
        verifyZeroInteractions(gateway.reloadMode);
    }

    @Test
    public void shouldRemoveFirstTargetFromLoadBalancer() throws Exception {
        givenConfig(CONFIG);

        gateway.from("jolokia", PROD).removeTarget(URI.create("http://worker1:80"));

        assertThat(contentOf(gateway.nginxConfigPath.toFile()))
                .isEqualTo(CONFIG.replace("        server localhost:8180;\n", ""));
        verify(gateway.reloadMode, only()).call();
    }

    @Test
    public void shouldRemoveFinalTargetFromLoadBalancer() throws Exception {
        givenConfig(CONFIG);

        gateway.from("jolokia", QA).removeTarget(URI.create("http://worker" + "qa1:80"));

        assertThat(contentOf(gateway.nginxConfigPath.toFile()))
                .isEqualTo(CONFIG
                        .replace(""
                                + "    upstream jolokia" + "qa-lb {\n"
                                + "        server localhost:8380;\n"
                                + "    }\n"
                                + "\n", "")
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
        verify(gateway.reloadMode, only()).call();
    }

    @Test
    public void shouldAddTargetToExistingLoadBalancer() throws Exception {
        givenConfig(CONFIG);

        gateway.to("jolokia", QA).addTarget(URI.create("http://worker" + "qa2:80"));

        assertThat(contentOf(gateway.nginxConfigPath.toFile()))
                .isEqualTo(CONFIG
                        .replace(""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8380;\n"
                                        + "    }\n",
                                ""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8380;\n"
                                        + "        server localhost:8480;\n"
                                        + "    }\n"));
        verify(gateway.reloadMode, only()).call();
    }

    @Test
    public void shouldAddTargetToEmptyLoadBalancer() throws Exception {
        givenConfig(CONFIG.replace(""
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

        gateway.to("jolokia", QA).addTarget(URI.create("http://worker1:80"));

        assertThat(contentOf(gateway.nginxConfigPath.toFile()))
                .isEqualTo(CONFIG
                        .replace(""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8380;\n"
                                        + "    }\n",
                                ""
                                        + "    upstream jolokia" + "qa-lb {\n"
                                        + "        server localhost:8180;\n"
                                        + "        server localhost:8380;\n"
                                        + "    }\n"));
        verify(gateway.reloadMode, only()).call();
    }

    @Test
    public void shouldAddTargetToNewLoadBalancer() throws Exception {
        givenConfig(CONFIG);

        gateway.to("foo", PROD).addTarget(URI.create("http://worker1:80"));

        assertThat(contentOf(gateway.nginxConfigPath.toFile()))
                .isEqualTo(CONFIG
                        .replace(""
                                        + "http {\n",
                                ""
                                        + "http {\n"
                                        + "    upstream foo-lb {\n"
                                        + "        least_conn;\n"
                                        + "\n"
                                        + "        server localhost:8180;\n"
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
        verify(gateway.reloadMode, only()).call();
    }
}
