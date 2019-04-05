package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.gateway.health.HealthGateway;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway;
import com.github.t1.kubee.gateway.loadbalancer.NginxLoadBalancerGatewayTest;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Deployment;
import com.github.t1.kubee.model.Stage;
import lombok.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.github.t1.kubee.model.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.model.ClusterTest.DEV;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.will;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    public static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().name("foo").node(DEV01).groupId("foo-group").artifactId("foo-artifact").version("1.0.1").build();
    private static final String NGINX_CONFIG = "";


    Controller controller = new Controller();
    List<LoadBalancerCall> loadBalancerCalls = new ArrayList<>();

    @TempDir Path folder;

    private Path originalConfigFile;

    @BeforeEach
    public void setUp() throws IOException {
        controller.clusters = asList(CLUSTERS);
        controller.deployer = mock(DeployerGateway.class);
        controller.healthGateway = mock(HealthGateway.class);
        controller.loadBalancing = mock(LoadBalancerGateway.class);

        will(this::loadBalancerAdd).given(controller.loadBalancing).add(anyString(), any(ClusterNode.class));
        will(this::loadBalancerRemove).given(controller.loadBalancing).remove(anyString(), any(ClusterNode.class));

        originalConfigFile = NginxLoadBalancerGatewayTest.setConfigDir(folder);
        Files.write(folder.resolve("nginx" + "dev.conf"), NGINX_CONFIG.getBytes(UTF_8));
    }

    private String loadBalancerAdd(InvocationOnMock invocation) {
        ClusterNode node = invocation.getArgument(1);
        loadBalancerCalls.add(new LoadBalancerCall("add", invocation.getArgument(0), node.getStage(), node));
        return null;
    }

    private String loadBalancerRemove(InvocationOnMock invocation) {
        ClusterNode node = invocation.getArgument(1);
        loadBalancerCalls.add(new LoadBalancerCall("remove", invocation.getArgument(0), node.getStage(), node));
        return null;
    }

    @AfterEach
    public void tearDown() { NginxLoadBalancerGatewayTest.setConfigDir(originalConfigFile); }

    @Value
    static class LoadBalancerCall {
        String action;
        String name;
        Stage stage;
        ClusterNode node;
    }
}
