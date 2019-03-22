package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.gateway.health.HealthGateway;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerAddAction;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerRemoveAction;
import com.github.t1.kubee.gateway.loadbalancer.NginxLoadBalancerGatewayTest;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Deployment;
import com.github.t1.kubee.model.Stage;
import lombok.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.net.URI;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.will;
import static org.mockito.Mockito.mock;

public abstract class AbstractControllerTest {
    static final ClusterNode DEV01 = CLUSTERS[0].node(DEV, 1);
    static final Deployment DEPLOYMENT = Deployment
        .builder().name("foo").node(DEV01).groupId("foo-group").artifactId("foo-artifact").version("1.0.1").build();
    private static final String NGINX_CONFIG = "";


    Controller controller = new Controller();
    List<LoadBalancerCall> loadBalancerCalls = new ArrayList<>();

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path originalConfigFile;

    @Before
    public void setUp() throws IOException {
        controller.clusters = asList(CLUSTERS);
        controller.deployer = mock(DeployerGateway.class);
        controller.healthGateway = mock(HealthGateway.class);
        controller.loadBalancing = mock(LoadBalancerGateway.class);

        given(controller.loadBalancing.to(anyString(), any(Stage.class))).will(this::loadBalancerAdd);
        given(controller.loadBalancing.from(anyString(), any(Stage.class))).will(this::loadBalancerRemove);

        Path etc = folder.getRoot().toPath();
        originalConfigFile = NginxLoadBalancerGatewayTest.setConfigDir(etc);
        Files.write(etc.resolve("nginx" + "dev.conf"), NGINX_CONFIG.getBytes(UTF_8));
    }

    private LoadBalancerAddAction loadBalancerAdd(InvocationOnMock invocation) {
        LoadBalancerAddAction mock = mock(LoadBalancerAddAction.class);
        will(i1 -> loadBalancerCalls.add(new LoadBalancerCall("add", invocation.getArgument(0), invocation.getArgument(1), i1.getArgument(0))))
            .given(mock).addTarget(any());
        return mock;
    }

    private LoadBalancerRemoveAction loadBalancerRemove(InvocationOnMock invocation) {
        LoadBalancerRemoveAction mock = mock(LoadBalancerRemoveAction.class);
        will(i1 -> loadBalancerCalls.add(new LoadBalancerCall("remove", invocation.getArgument(0), invocation.getArgument(1), i1.getArgument(0))))
            .given(mock).removeTarget(any());
        return mock;
    }

    @After
    public void tearDown() { NginxLoadBalancerGatewayTest.setConfigDir(originalConfigFile); }

    @Value
    static class LoadBalancerCall {
        String action;
        String name;
        Stage stage;
        URI uri;
    }
}
