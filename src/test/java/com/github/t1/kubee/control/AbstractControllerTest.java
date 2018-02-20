package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.gateway.health.HealthGateway;
import com.github.t1.kubee.gateway.loadbalancer.*;
import com.github.t1.kubee.model.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.*;

import static com.github.t1.kubee.model.ClusterTest.*;
import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static org.mockito.Mockito.*;

public abstract class AbstractControllerTest {
    static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
            .builder().name("foo").node(DEV01).groupId("foo-group").artifactId("foo-artifact").version("1.0.1").build();
    private static final String NGINX_CONFIG = "";


    Controller controller = new Controller();

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path originalConfigFile;

    @Before
    public void setUp() throws IOException {
        controller.clusters = asList(CLUSTERS);
        controller.deployer = mock(DeployerGateway.class);
        controller.healthGateway = mock(HealthGateway.class);
        controller.loadBalancing = mock(LoadBalancerGateway.class);

        when(controller.loadBalancing.from(anyString(), any(Stage.class))).thenCallRealMethod();
        when(controller.loadBalancing.to(anyString(), any(Stage.class))).thenCallRealMethod();
        when(controller.loadBalancing.config(any(Stage.class))).thenCallRealMethod();

        Path etc = folder.getRoot().toPath();
        originalConfigFile = NginxLoadBalancerGatewayTest.setConfigDir(etc);
        Files.write(etc.resolve("nginx" + "dev.conf"), NGINX_CONFIG.getBytes(UTF_8));
    }

    @After
    public void tearDown() { NginxLoadBalancerGatewayTest.setConfigDir(originalConfigFile); }
}
