package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.container.ClusterStatus;
import com.github.t1.kubee.gateway.loadbalancer.Ingress;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.LoadBalancer;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.ReverseProxy;
import com.github.t1.kubee.gateway.loadbalancer.ReloadMock;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.Slot;
import com.github.t1.kubee.model.Stage;
import lombok.Getter;
import lombok.Setter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

class ClusterReconditionerTest {
    private static final Endpoint WORKER01 = new Endpoint("worker01", 10001);
    private static final Endpoint WORKER02 = new Endpoint("worker02", 10002);
    private static final Endpoint WORKER03 = new Endpoint("worker03", 10003);
    private static final List<Endpoint> WORKERS = asList(WORKER01, WORKER02, WORKER03);


    // <editor-fold desc="Cluster Config">
    private static final Slot SLOT = Slot.builder().name("0").http(8080).https(8443).build();
    private Cluster cluster = null;
    private final List<ClusterNode> nodes = new ArrayList<>();

    private void givenClusterWithNodeCount(int count) {
        Stage stage = Stage.builder()
            .name("PROD")
            .count(count)
            .indexLength(2)
            .loadBalancerConfig("reload", "custom")
            .loadBalancerConfig("class", ReloadMock.class.getName())
            .build();
        cluster = Cluster.builder().host("worker").slot(SLOT).stage(stage).build();
        for (int i = 0; i < count; i++) {
            nodes.add(new ClusterNode(cluster, stage, i + 1));
        }
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

        @Override public void write() { ingressWritten = true; }

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

        @Override public String toString() { return name() + ":" + endpoints; }

        @Override public String name() { return "dummy-app-lb"; }

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

        @Override public boolean hasEndpoint(Endpoint endpoint) { return hasHost(endpoint.getHost()); }

        @Override public void addOrUpdateEndpoint(Endpoint endpoint) {
            if (hasEndpoint(endpoint))
                updatePort(endpoint, endpoint.getPort());
            else
                endpoints.add(endpoint);
        }
    }
    // </editor-fold>

    private ClusterNode findNode(String host) {
        return nodes.stream().filter(it -> it.host().equals(host)).findAny().orElse(null);
    }

    private void assertIngress(boolean written, Endpoint... endpoints) {
        if (ingressWritten != written)
            assertThat(ingressBefore).describedAs("expected ingress written " + written).isEqualTo(ingress.toString());
        assertThat(loadBalancer.getEndpoints()).describedAs("load balancers").containsExactly(endpoints);
        assertThat(reverseProxies).describedAs("reverse proxies").hasSize(endpoints.length);
        for (int i = 0; i < endpoints.length; i++) {
            ReverseProxy reverseProxy = reverseProxies.get(nodes.get(i));
            assertThat(new Endpoint(reverseProxy.name(), reverseProxy.getPort()))
                .describedAs("reverse proxy " + i).isEqualTo(endpoints[i]);
        }
    }
    // </editor-fold>


    private void recondition() {
        new ClusterReconditioner(singletonMap(cluster, clusterStatus), ingress).run();
    }


    @Test void shouldRunEmpty() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(false, WORKER01);
    }

    @Test void shouldUpdatePortOfWorker01() {
        givenClusterWithNodeCount(1);
        Endpoint movedEndpoint = WORKER01.withPort(20000);
        givenContainers(movedEndpoint);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(movedEndpoint);
        assertIngress(true, movedEndpoint);
    }

    @Test void shouldUpdatePortOfSecondWorkerOf3() {
        givenClusterWithNodeCount(3);
        Endpoint movedEndpoint = WORKER02.withPort(20000);
        givenContainers(WORKER01, movedEndpoint, WORKER03);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertThat(containers).containsExactly(WORKER01, movedEndpoint, WORKER03);
        assertIngress(true, WORKER01, movedEndpoint, WORKER03);
    }

    @Test void shouldAddWorkerNodesServer() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(WORKER01, WORKER02);
        assertIngress(true, WORKER01, WORKER02);
    }

    @Test void shouldAddNodeToEmptyIngress() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress();

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(true, WORKER01);
    }

    @Test void shouldAddNodeToIngress() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(WORKER01, WORKER02);
        assertIngress(true, WORKER01, WORKER02);
    }

    @Test void shouldRemoveNodeFromIngress() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01, WORKER02);

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(true, WORKER01);
    }

    @Test void shouldRemoveTwoNodesFromIngress() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(true, WORKER01);
    }

    @Test void shouldScaleOneUp() {
        givenClusterWithNodeCount(2);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(WORKER01, WORKER02);
        assertIngress(true, WORKER01, WORKER02);
    }

    @Test void shouldScaleTwoUp() {
        givenClusterWithNodeCount(3);
        givenContainers(WORKER01);
        givenIngress(WORKER01);

        recondition();

        assertThat(containers).containsExactly(WORKER01, WORKER02, WORKER03);
        assertIngress(true, WORKER01, WORKER02, WORKER03);
    }

    @Test void shouldScaleOneDown() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01, WORKER02);
        givenIngress(WORKER01, WORKER02);

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(true, WORKER01);
    }

    @Test void shouldScaleTwoDown() {
        givenClusterWithNodeCount(1);
        givenContainers(WORKER01, WORKER02, WORKER03);
        givenIngress(WORKER01, WORKER02, WORKER03);

        recondition();

        assertThat(containers).containsExactly(WORKER01);
        assertIngress(true, WORKER01);
    }

    // TODO multiple stages
    // TODO multiple clusters
}
