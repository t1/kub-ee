package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.boundary.gateway.container.ClusterStatusGateway;
import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.boundary.gateway.ingress.IngressFactory;
import com.github.t1.kubee.boundary.gateway.ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.boundary.gateway.ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Cluster.ClusterBuilder;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.entity.Stage.StageBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.DEV;
import static com.github.t1.kubee.TestData.PROD;
import static com.github.t1.kubee.TestData.SLOT_0;
import static com.github.t1.kubee.TestData.VERSION_101;
import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class ClusterReconditionerTest {
    private static final Endpoint PROD01 = new Endpoint("worker01", 10001);
    private static final Endpoint PROD02 = new Endpoint("worker02", 10002);
    private static final Endpoint PROD03 = new Endpoint("worker03", 10003);

    private static final Endpoint DEV01 = new Endpoint("worker-dev01", 20001);
    private static final Endpoint DEV02 = new Endpoint("worker-dev02", 20002);

    private static final String APP_NAME = "dummy-app";

    private static List<Endpoint> poolFor(String stageName) {
        switch (stageName) {
            case "DEV":
                return asList(DEV01, DEV02);
            case "PROD":
                return asList(PROD01, PROD02, PROD03);
            default:
                throw new RuntimeException("no pool for " + stageName);
        }
    }


    // <editor-fold desc="Cluster Config">
    private Cluster cluster = null;

    private void givenCluster(StageBuilder... stages) {
        assertThat(cluster).isNull();
        ClusterBuilder builder = Cluster.builder().host("worker").slot(SLOT_0);
        for (StageBuilder stage : stages)
            builder.stage(stage.build());
        cluster = builder.build();
    }

    private StageBuilder dev() { return stage("DEV"); }

    private StageBuilder prod() { return stage("PROD"); }

    private StageBuilder stage(String name) {
        return Stage.builder()
            .name(name)
            .provider("docker-compose")
            .indexLength(2)
            .loadBalancerConfig("reload", "custom")
            .loadBalancerConfig("class", ReloadMock.class.getName());
    }
    // </editor-fold>


    // <editor-fold desc="Cluster Status">
    private final Map<String, List<Endpoint>> containers = new LinkedHashMap<>();

    private final ClusterStatusGateway clusterStatusGateway = new ClusterStatusGateway() {
        @Override public ClusterStatus clusterStatus(Cluster cluster) {
            assertThat(cluster).isEqualTo(cluster);
            return new ClusterStatusMock();
        }
    };

    private class ClusterStatusMock extends ClusterStatus {
        @Override public Integer exposedPort(Stage stage, String host) {
            return super.exposedPort(stage, host); // works just fine
        }

        @Override public String toString() { return "cluster-status-mock"; }

        @Override public Stream<Endpoint> endpoints(Stage stage) {
            return containers.get(stage.getName()).stream();
        }

        @Override public Stream<Endpoint> endpoints() {
            return containers.values().stream().flatMap(Collection::stream);
        }

        @Override public void scale() {
            for (Stage stage : cluster.getStages()) {
                List<Endpoint> endpoints = getEndpointsIn(stage);
                while (endpoints.size() > stage.getCount())
                    endpoints.remove(endpoints.size() - 1);
                for (int i = endpoints.size(); i < stage.getCount(); i++)
                    endpoints.add(poolFor(stage.getName()).get(i));
            }
        }
    }

    private void givenContainers(Stage stage, Endpoint... endpoints) {
        getEndpointsIn(stage).addAll(asList(endpoints));
    }

    private List<Endpoint> getEndpointsIn(Stage stage) {
        return containers.computeIfAbsent(stage.getName(), s -> new ArrayList<>());
    }

    private void givenDeployedContainers(Stage stage, Endpoint... endpoints) {
        givenContainers(stage, endpoints);
        givenDeployedVersions(endpoints);
    }

    private void assertContainers(Stage stage, Endpoint... endpoints) {
        assertThat(containers.get(stage.getName())).containsExactly(endpoints);
    }
    // </editor-fold>


    // <editor-fold desc="Ingress">
    private final Map<ClusterNode, ReverseProxy> reverseProxies = new LinkedHashMap<>();
    private LoadBalancer loadBalancer = null;
    private String ingressBefore;
    private int ingressApplyCount = 0;

    private void givenIngress(Endpoint... endpoints) {
        givenReverseProxyFor(endpoints);
        givenAppLoadBalancer(endpoints);
        this.ingressBefore = ingress.toString();
    }

    private final Ingress ingress = new Ingress() {
        @Override public String toString() { return "Ingress\n" + reverseProxies + "\n" + loadBalancer; }

        @Override public boolean hasChanged() { return !this.toString().equals(ingressBefore); }

        @Override public void apply() { ingressApplyCount++; }

        @Override public void removeReverseProxyFor(ClusterNode node) { reverseProxies.remove(node); }

        @Override public boolean hasReverseProxyFor(ClusterNode node) { return reverseProxies.containsKey(node); }

        @Override public Stream<ReverseProxy> reverseProxies() {
            throw new UnsupportedOperationException(); // TODO missing tests for this?
        }

        @Override public void addToLoadBalancer(String application, ClusterNode node) {
            throw new UnsupportedOperationException(); // TODO missing tests for this?
        }

        @Override public ReverseProxy getOrCreateReverseProxyFor(ClusterNode node) {
            return reverseProxies.computeIfAbsent(node, from -> new ReverseProxyMock(from.endpoint()));
        }

        @Override public Stream<LoadBalancer> loadBalancers() { return loadBalancer == null ? Stream.of() : Stream.of(loadBalancer); }

        @Override public void removeFromLoadBalancer(String application, ClusterNode node) {
            throw new UnsupportedOperationException(); // TODO missing tests for this?
        }
    };

    // <editor-fold desc="Reverse Proxy">
    private void givenReverseProxyFor(Endpoint... endpoints) {
        assert ingressBefore == null;
        for (Endpoint endpoint : endpoints) {
            ReverseProxy reverseProxy = getOrCreateReverseProxyFor(endpoint);
            if (reverseProxy != null)
                reverseProxies.put(findNode(endpoint.getHost()), reverseProxy);
        }
    }

    private ReverseProxy getOrCreateReverseProxyFor(Endpoint endpoint) {
        ClusterNode node = findNode(endpoint.getHost());
        if (node == null)
            return null;
        ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
        reverseProxy.setPort(endpoint.getPort());
        return reverseProxy;
    }

    @RequiredArgsConstructor
    private static class ReverseProxyMock implements ReverseProxy {
        private final Endpoint from;
        @Getter @Setter private int port = -1;

        @Override public String toString() { return from + "->" + port; }

        @Override public String name() { return from.getHost(); }

        @Override public Integer listen() {
            throw new UnsupportedOperationException(); // TODO missing tests for this?
        }
    }

    private ClusterNode findNode(String host) {
        return cluster.nodes().filter(it -> it.host().equals(host)).findAny().orElse(null);
    }
    // </editor-fold>

    // <editor-fold desc="Load Balancer">
    private void givenAppLoadBalancer(Endpoint... endpoints) {
        assert ingressBefore == null;
        assert loadBalancer == null;
        this.loadBalancer = new LoadBalancerMock(new ArrayList<>(asList(endpoints)));
    }

    @RequiredArgsConstructor
    private static class LoadBalancerMock implements LoadBalancer {
        @Getter final List<Endpoint> endpoints;

        @Override public String toString() { return applicationName() + ":" + endpoints; }

        @Override public String applicationName() { return APP_NAME; }

        @Override public String method() { return "least_conn"; }

        @Override public void updatePort(Endpoint endpoint, Integer newPort) {
            int index = indexOf(endpoint.getHost());
            endpoints.set(index, endpoints.get(index).withPort(newPort));
        }

        @Override public int indexOf(String host) {
            for (int i = 0; i < endpoints.size(); i++)
                if (endpoints.get(i).getHost().equals(host))
                    return i;
            throw new IllegalArgumentException("host [" + host + "] not in " + endpoints);
        }

        @Override public boolean hasHost(String host) {
            return endpoints().anyMatch(endpoint -> endpoint.getHost().equals(host));
        }

        @Override public void removeHost(String host) { endpoints.remove(indexOf(host)); }

        @Override public boolean hasEndpoint(Endpoint endpoint) { return endpoints.contains(endpoint); }

        @Override public Stream<Endpoint> endpoints() { return new ArrayList<>(endpoints).stream(); }

        @Override public void addOrUpdateEndpoint(Endpoint endpoint) {
            if (hasEndpoint(endpoint))
                updatePort(endpoint, endpoint.getPort());
            else
                endpoints.add(endpoint);
        }
    }
    // </editor-fold>

    private void assertIngressNotApplied(Endpoint... endpoints) {
        assertIngressWasNotApplied();
        assertLoadBalancers(endpoints);
        assertReverseProxies(PROD, endpoints);
    }

    private void assertIngressWasNotApplied() {
        if (ingressApplyCount > 0)
            assertThat(ingressBefore).describedAs("expected ingress not applied").isEqualTo(ingress.toString());
    }

    private void assertIngressApplied(Endpoint... endpoints) {
        assertIngressWasApplied();
        assertLoadBalancers(endpoints);
        assertReverseProxies(PROD, endpoints);
    }

    private void assertIngressWasApplied() {
        assertThat(ingressApplyCount).describedAs("expected ingress applied").isGreaterThan(0);
    }

    private void assertLoadBalancers(Endpoint... endpoints) {
        assertThat(loadBalancer.endpoints()).describedAs("load balancers").containsOnly(endpoints);
    }

    private void assertReverseProxies(Stage stage, Endpoint... endpoints) {
        for (int i = 0; i < endpoints.length; i++) {
            ReverseProxy reverseProxy = reverseProxies.get(cluster.node(stage.getName(), i + 1));
            assertThat(new Endpoint(reverseProxy.name(), reverseProxy.getPort()))
                .describedAs("reverse proxy " + i).isEqualTo(endpoints[i]);
        }
    }
    // </editor-fold>


    // <editor-fold desc="Deployer Gateway">
    private DeployerGateway deployerGateway = mock(DeployerGateway.class);

    private void givenDeployedVersions(Endpoint... endpoints) {
        for (Endpoint endpoint : endpoints)
            givenDeployedVersion(endpoint.getHost());
    }

    private void givenDeployedVersion(String host) {
        given(this.deployerGateway.fetchVersion(findNode(host), APP_NAME)).willReturn(VERSION_101);
    }
    // </editor-fold>


    private void recondition() {
        Function<Stage, Ingress> originalBuilder = IngressFactory.BUILDER;
        IngressFactory.BUILDER = stage -> ingress;
        try {
            ClusterReconditioner reconditioner = new ClusterReconditioner(
                new ClusterStore() {
                    @Override public List<Cluster> getClusters() { return singletonList(cluster); }
                },
                deployerGateway,
                clusterStatusGateway);
            reconditioner.run();
        } finally {
            IngressFactory.BUILDER = originalBuilder;
        }
    }


    @Test void shouldDoNothingWithoutNodes() {
        givenCluster(prod().count(0));
        givenDeployedContainers(PROD);
        givenIngress();

        recondition();

        assertContainers(PROD);
        assertIngressNotApplied();
    }

    @Test void shouldDoNothingWithOneNode() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressNotApplied(PROD01);
    }

    @Test void shouldDoNothingWithTwoNodes() {
        givenCluster(prod().count(2));
        givenDeployedContainers(PROD, PROD01, PROD02);
        givenIngress(PROD01, PROD02);

        recondition();

        assertContainers(PROD, PROD01, PROD02);
        assertIngressNotApplied(PROD01, PROD02);
    }

    @Test void shouldUpdatePortOfWorker01() {
        givenCluster(prod().count(1));
        Endpoint movedEndpoint = PROD01.withPort(20000);
        givenDeployedContainers(PROD, movedEndpoint);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, movedEndpoint);
        assertIngressApplied(movedEndpoint);
    }

    @Test void shouldUpdatePortOfSecondWorkerOf3() {
        givenCluster(prod().count(3));
        Endpoint movedEndpoint = PROD02.withPort(20000);
        givenDeployedContainers(PROD, PROD01, movedEndpoint, PROD03);
        givenIngress(PROD01, PROD02, PROD03);

        recondition();

        assertContainers(PROD, PROD01, movedEndpoint, PROD03);
        assertIngressApplied(PROD01, movedEndpoint, PROD03);
    }

    @Test void shouldAddNodeToEmptyIngress() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01);
        givenIngress();

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressApplied(PROD01);
    }

    @Test void shouldAddNodeToIngressWithOne() {
        givenCluster(prod().count(2));
        givenDeployedContainers(PROD, PROD01, PROD02);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01, PROD02);
        assertIngressApplied(PROD01, PROD02);
    }

    @Test void shouldAddTwoNodeToIngressWithOne() {
        givenCluster(prod().count(3));
        givenDeployedContainers(PROD, PROD01, PROD02, PROD03);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressApplied(PROD01, PROD02, PROD03);
    }

    @Test void shouldRemoveLastNodeFromIngress() {
        givenCluster(prod().count(0));
        givenDeployedContainers(PROD);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD);
        assertIngressApplied();
    }

    @Test void shouldRemoveNodeFromIngressWithTwo() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01, PROD02);

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressApplied(PROD01);
    }

    @Test void shouldRemoveTwoNodesFromIngress() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01, PROD02, PROD03);

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressApplied(PROD01);
    }


    @Test void shouldScaleContainersFrom0to1() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD);
        givenIngress();

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressWasApplied();
        assertLoadBalancers(); // not deployed, yet
        assertReverseProxies(PROD, PROD01);
    }

    @Test void shouldScaleContainersFrom1to2() {
        givenCluster(prod().count(2));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01, PROD02);
        assertIngressWasApplied();
        assertLoadBalancers(PROD01); // not deployed, yet
        assertReverseProxies(PROD, PROD01, PROD02);
    }

    @Test void shouldScaleContainersFrom1to3() {
        givenCluster(prod().count(3));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressWasApplied();
        assertLoadBalancers(PROD01);  // not deployed, yet
        assertReverseProxies(PROD, PROD01, PROD02, PROD03);
    }

    @Test void shouldScaleContainersFrom1to0() {
        givenCluster(prod().count(0));
        givenDeployedContainers(PROD, PROD01);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD);
        assertIngressApplied();
    }

    @Test void shouldScaleContainersFrom2to1() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01, PROD02);
        givenIngress(PROD01, PROD02);

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressApplied(PROD01);
    }

    @Test void shouldScaleContainersFrom3to1() {
        givenCluster(prod().count(1));
        givenDeployedContainers(PROD, PROD01, PROD02, PROD03);
        givenIngress(PROD01, PROD02, PROD03);

        recondition();

        assertContainers(PROD, PROD01);
        assertIngressApplied(PROD01);
    }

    @Test void shouldScaleDevFrom2to1andProdFrom1to3() {
        givenCluster(dev().suffix("-dev").count(1), prod().count(3));
        givenDeployedContainers(DEV, DEV01, DEV02);
        givenDeployedContainers(PROD, PROD01, PROD02, PROD03);
        givenIngress(DEV01, DEV02, PROD01);

        recondition();

        assertContainers(DEV, DEV01);
        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressWasApplied();
        assertLoadBalancers(DEV01, PROD01, PROD02, PROD03);
        assertReverseProxies(DEV, DEV01);
        assertReverseProxies(PROD, PROD01, PROD02, PROD03);
    }

    @Test void shouldUnbalanceNode() {
        givenCluster(prod().count(3).status("2:" + APP_NAME, unbalanced));
        givenDeployedContainers(PROD, PROD01, PROD02, PROD03);
        givenIngress(PROD01, PROD02, PROD03);

        recondition();

        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressWasApplied();
        assertLoadBalancers(PROD01, PROD03);
        assertReverseProxies(PROD, PROD01, PROD02, PROD03);
    }

    @Test void shouldUnbalanceLastNode() {
        givenCluster(prod().count(3).status("1:" + APP_NAME, unbalanced));
        givenDeployedContainers(PROD, PROD01, PROD02, PROD03);
        givenIngress(PROD01);

        recondition();

        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressWasApplied();
        assertLoadBalancers(PROD02, PROD03);
        assertReverseProxies(PROD, PROD01, PROD02, PROD03);
    }

    @Test void shouldNotAddLoadBalancerNodeWhenApplicationIsNotDeployed() {
        givenCluster(prod().count(3));
        givenContainers(PROD, PROD01, PROD02, PROD03);
        givenDeployedVersions(PROD01, PROD03);
        givenReverseProxyFor(PROD01, PROD02, PROD03);
        givenAppLoadBalancer(PROD01, PROD03);
        this.ingressBefore = ingress.toString();

        recondition();

        assertContainers(PROD, PROD01, PROD02, PROD03);
        assertIngressWasNotApplied();
        assertLoadBalancers(PROD01, PROD03);
        assertReverseProxies(PROD, PROD01, PROD02, PROD03);
    }

    // TODO status stopped
    // TODO multiple stages
    // TODO multiple slots
    // TODO multiple clusters
}
