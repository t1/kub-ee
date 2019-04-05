package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.gateway.health.HealthGateway;
import com.github.t1.kubee.gateway.loadbalancer.IngressGateway;
import com.github.t1.kubee.gateway.loadbalancer.NginxLoadBalancerGatewayTest;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Deployment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.github.t1.kubee.model.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.model.ClusterTest.DEV;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    public static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().name("foo").node(DEV01).groupId("foo-group").artifactId("foo-artifact").version("1.0.1").build();
    private static final String NGINX_CONFIG = "";


    Controller controller = new Controller();

    @TempDir Path folder;

    private Path originalConfigFile;

    @BeforeEach
    public void setUp() throws IOException {
        controller.clusters = asList(CLUSTERS);
        controller.deployer = mock(DeployerGateway.class);
        controller.healthGateway = mock(HealthGateway.class);
        controller.ingressGateway = mock(IngressGateway.class);

        originalConfigFile = NginxLoadBalancerGatewayTest.setConfigDir(folder);
        Files.write(folder.resolve("nginx" + "dev.conf"), NGINX_CONFIG.getBytes(UTF_8));
    }

    @AfterEach
    public void tearDown() { NginxLoadBalancerGatewayTest.setConfigDir(originalConfigFile); }
}
