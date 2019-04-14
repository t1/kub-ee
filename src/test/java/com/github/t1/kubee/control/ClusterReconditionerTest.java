package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.ReverseProxy;
import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.entity.Stage.StageBuilder;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterReconditionerTest {
    private static final Endpoint WORKER01 = new Endpoint("worker01", 10001);
    private static final Endpoint WORKER02 = new Endpoint("worker02", 10002);
    private static final Endpoint WORKER03 = new Endpoint("worker03", 10003);
    private static final List<Endpoint> WORKERS = asList(WORKER01, WORKER02, WORKER03);

    private static final String APP_NAME = "dummy-app";


    // <editor-fold desc="Cluster Config">
    private static final Slot SLOT = Slot.builder().name("0").http(8080).https(8443).build();
    private Cluster cluster = null;

    private void givenClusterWithNodeCount(int count) {
        givenClusterWith(stage().count(count));
    }

    private void givenClusterWith(StageBuilder stage) {
        assertThat(cluster).isNull();
        cluster = Cluster.builder().host("worker").slot(SLOT).stage(stage.build()).build();
    }

    private StageBuilder stage() {
        return Stage.builder()
            .name("PROD")
            .indexLength(2)
            .loadBalancerConfig("reload", "custom")
            .loadBalancerConfig("class", ReloadMock.class.getName());
    }
    // </editor-fold>


    // <editor-fold desc="Cluster Status">
    private final ClusterStatus clusterStatus = new ClusterStatusMock();
    private final List<Endpoint> containers = new ArrayList<>();

    private class ClusterStatusMock extends ClusterStatus {
        @Override public Integer port(String host) {
            for (Endpoint endpoint : containers)
                if (endpoint.getHost().equals(host))
                    return endpoint.getPort();
            return null;
        }

        @Override public Stream<Endpoint> endpoints() { return containers.stream(); }

        @Override public List<Endpoint> scale(Stage stage) {
            while (containers.size() > stage.getCount())
                containers.remove(containers.size() - 1);
            for (int i = containers.size(); i < stage.getCount(); i++)
                containers.add(WORKERS.get(i));
            return null;
        }
    }

    private void givenContainers(Endpoint... endpoints) { containers.addAll(asList(endpoints)); }

    private void assertContainers(Endpoint... endpoints) { assertThat(containers).containsExactly(endpoints); }
    // </editor-fold>


    // <editor-fold desc="Ingress">
    private final Map<ClusterNode, ReverseProxy> reverseProxies = new LinkedHashMap<>();
    private LoadBalancer loadBalancer = null;
    private String ingressBefore;
    private boolean ingressWritten = false;

    private void givenIngress(Endpoint... workers) {
        givenReverseProxyFor(workers);
        givenAppLoadBalancer(workers);
        this.ingressBefore = ingress.toString();
    }

    private final Ingress ingress = new Ingress() {
        @Override public String toString() { return "Ingress\n" + reverseProxies + "\n" + loadBalancer; }

        @Override public boolean hasChanged() { return !this.toString().equals(ingressBefore); }

        @Override public void apply() { ingressWritten = true; }

        @Override public void removeReverseProxyFor(ClusterNode node) { reverseProxies.remove(node); }

        @Override public boolean hasReverseProxyFor(ClusterNode node) { return reverseProxies.containsKey(node); }

        @Override public ReverseProxy getOrCreateReverseProxyFor(ClusterNode node) {
            return reverseProxies.computeIfAbsent(node, from -> new ReverseProxyMock(from.endpoint()));
        }

        @Override public Stream<LoadBalancer> loadBalancers() { return loadBalancer == null ? Stream.of() : Stream.of(loadBalancer); }
    };

    // <editor-fold desc="Reverse Proxy">
    private void givenReverseProxyFor(Endpoint... workers) {
        assert ingressBefore == null;
        for (Endpoint worker : workers) {
            ReverseProxy reverseProxy = getOrCreateReverseProxyFor(worker);
            if (reverseProxy != null)
                reverseProxies.put(findNode(worker.getHost()), reverseProxy);
        }
    }

    private ReverseProxy getOrCreateReverseProxyFor(Endpoint worker) {
        ClusterNode node = findNode(worker.getHost());
        if (node == null)
            return null;
        ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
        reverseProxy.setPort(worker.getPort());
        return reverseProxy;
    }

    private class ReverseProxyMock extends ReverseProxy {
        private Endpoint from;
        @Getter @Setter private int port = -1;

        ReverseProxyMock(Endpoint from) {
            ingress.super(null);
            this.from = from;
        }

        @Override public String toString() { return from + "->" + port; }

        @Override public String name() { return from.getHost(); }
    }
    // </editor-fold>

    // <editor-fold desc="Load Balancer">
    private void givenAppLoadBalancer(Endpoint... workers) {
        assert ingressBefore == null;
        assert loadBalancer == null;
        this.loadBalancer = new LoadBalancerMock(new ArrayList<>(asList(workers)));
    }

    private class LoadBalancerMock extends LoadBalancer {
        LoadBalancerMock(List<Endpoint> endpoints) {
            ingress.super();
            this.endpoints = endpoints;
        }

        @Getter final List<Endpoint> endpoints;

        @Override public String toString() { return applicationName() + ":" + endpoints; }

        @Override public String applicationName() { return APP_NAME; }

        @Override public String method() { return "least_conn"; }

        @Override public void updatePort(Endpoint endpoint, Integer newPort) {
            int index = findEndpoint(endpoint.getHost());
            endpoints.set(index, endpoints.get(index).withPort(newPort));
        }

        private int findEndpoint(String host) {
            for (int i = 0; i < endpoints.size(); i++)
                if (endpoints.get(i).getHost().equals(host))
                    return i;
            return -1;
        }

        @Override public boolean hasHost(String host) { return findEndpoint(host) >= 0; }

        @Override public void removeHost(String host) { endpoints.remove(findEndpoint(host)); }

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

    private ClusterNode findNode(String host) {
        return cluster.nodes().filter(it -> it.host().equals(host)).findAny().orElse(null);
    }

    private void assertIngressNotWritten(Endpoint... endpoints) {
        if (ingressWritten)
            assertThat(ingressBefore).describedAs("expected ingress not written").isEqualTo(ingress.toString());
        assertLoadBalancers(endpoints);
        assertReverseProxies(endpoints);
    }

    private void assertIngressWritten(Endpoint... endpoints) {
        assertIngressWasWritten();
        assertLoadBalancers(endpoints);
        assertReverseProxies(endpoints);
    }

    private void assertIngressWasWritten() {
        assertThat(ingressWritten).describedAs("expected ingress written").isTrue();
    }

    private void assertLoadBalancers(Endpoint... endpoints) {
        assertThat(loadBalancer.endpoints()).describedAs("load balancers").containsOnly(endpoints);
    }

    private void assertReverseProxies(Endpoint... endpoints) {
        assertThat(reverseProxies).describedAs("reverse proxies").hasSize(endpoints.length);
        for (int i = 0; i < endpoints.length; i++) {
            ReverseProxy reverseProxy = reverseProxies.get(cluster.node(cluster.getStages().get(0), i + 1));
            assertThat(new Endpoint(reverseProxy.name(), reverseProxy.getPort()))
                .describedAs("reverse proxy " + i).isEqualTo(endpoints[i]);
        }
    }
    // </editor-fold>


    private void recondition() {
        Function<Stage, Ingress> originalBuilder = Ingress.BUILDER;
        Ingress.BUILDER = stage -> ingress;
        try {
            new ClusterReconditioner(singletonMap(cluster, clusterStatus)).run();
        } finally {
            Ingress.BUILDER = originalBuilder;
        }
    }


    @Test void shouldDoNothingWithoutNodes() {
        givenClusterWithNodeCount(0);
        givenContainers();
        givenIngress();

        recondition();

        assertContainers();
        assertIngressNotWritten();
    }

    @Test void shouldDoNothingWithOneNode() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertContainers(WORKER01);
        assertIngressNotWritten(WORKER01);
    }

    @Test void shouldDoNothingWithTwoNodes() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01, WORKER02);

        recondition();

        assertContainers(WORKER01, WORKER02);
        assertIngressNotWritten(WORKER01, WORKER02);
    }

    @Test void shouldUpdatePortOfWorker01() {
        givenClusterWithNodeCount(1);
        Endpoint movedEndpoint = WORKER01.withPort(20000);
        givenContainers(movedEndpoint);
        givenIngress(WORKER01);

        recondition();

        assertContainers(movedEndpoint);
        assertIngressWritten(movedEndpoint);
    }

    @Test void shouldUpdatePortOfSecondWorkerOf3() {
        givenClusterWithNodeCount(3);
        Endpoint movedEndpoint = WORKER02.withPort(20000);
        givenContainers(WORKER01, movedEndpoint, WORKER03);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertContainers(WORKER01, movedEndpoint, WORKER03);
        assertIngressWritten(WORKER01, movedEndpoint, WORKER03);
    }

    @Test void shouldAddNodeToEmptyIngress() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress();

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }

    @Test void shouldAddNodeToIngressWithOne() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01);

        recondition();

        assertContainers(WORKER01, WORKER02);
        assertIngressWritten(WORKER01, WORKER02);
    }

    @Test void shouldAddTwoNodeToIngressWithOne() {
        givenClusterWithNodeCount(3);
        givenContainers(WORKER01, WORKER02, WORKER03);
        givenIngress(WORKER01);

        recondition();

        assertContainers(WORKER01, WORKER02, WORKER03);
        assertIngressWritten(WORKER01, WORKER02, WORKER03);
    }

    @Test void shouldRemoveLastNodeFromIngress() {
        givenClusterWithNodeCount(0);
        givenContainers();
        givenIngress(WORKER01);

        recondition();

        assertContainers();
        assertIngressWritten();
    }

    @Test void shouldRemoveNodeFromIngressWithTwo() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01, WORKER02);

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }

    @Test void shouldRemoveTwoNodesFromIngress() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }


    @Test void shouldScaleContainersFrom0to1() {
        givenClusterWithNodeCount(1);
        givenContainers();
        givenIngress();

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }

    @Test void shouldScaleContainersFrom1to2() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertContainers(WORKER01, WORKER02);
        assertIngressWritten(WORKER01, WORKER02);
    }

    @Test void shouldScaleContainersFrom1to3() {
        givenClusterWithNodeCount(3);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertContainers(WORKER01, WORKER02, WORKER03);
        assertIngressWritten(WORKER01, WORKER02, WORKER03);
    }

    @Test void shouldScaleContainersFrom1to0() {
        givenClusterWithNodeCount(0);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertContainers();
        assertIngressWritten();
    }

    @Test void shouldScaleContainersFrom2to1() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01, WORKER02);

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }

    @Test void shouldScaleContainersFrom3to1() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01, WORKER02, WORKER03);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertContainers(WORKER01);
        assertIngressWritten(WORKER01);
    }

    @Test void shouldUnbalanceNode() {
        givenClusterWith(stage()
            .count(3)
            .status("2:" + APP_NAME, unbalanced));
        givenContainers(WORKER01, WORKER02, WORKER03);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertContainers(WORKER01, WORKER02, WORKER03);
        assertIngressWasWritten();
        assertLoadBalancers(WORKER01, WORKER03);
        assertReverseProxies(WORKER01, WORKER02, WORKER03);
    }

    // TODO multiple stages
    // TODO multiple clusters
}
