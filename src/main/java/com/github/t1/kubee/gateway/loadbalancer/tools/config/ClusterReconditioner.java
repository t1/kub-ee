package com.github.t1.kubee.gateway.loadbalancer.tools.config;

import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerConfig;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerConfig.LoadBalancer;
import com.github.t1.kubee.gateway.loadbalancer.LoadBalancerConfig.ReverseProxy;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor class ClusterReconditioner implements Runnable {
    private final Consumer<String> note;
    private final List<Cluster> clusterConfig;
    private final ContainerStatus containerStatus;
    private final Path loadBalancerPath;
    private LoadBalancerConfig loadBalancerConfig;

    @Override public void run() {
        loadBalancerConfig = new LoadBalancerConfig(loadBalancerPath, note);

        clusterConfig.forEach(this::reconditionCluster);

        if (loadBalancerConfig.hasChanged()) {
            loadBalancerConfig.apply();
        }
    }

    private void reconditionCluster(Cluster cluster) {
        cluster.nodes().forEach(this::reconditionNode);
        cluster.lastNodes().forEach(node -> new NodeCleanup(node.next()).run());
        reconditionBalancedUpstream(cluster);
    }

    private void reconditionNode(ClusterNode node) {
        Integer actualPort = containerStatus.actualPort(node.endpoint().getHost());
        if (actualPort == null) {
            containerStatus.start(node);
        }
        LoadBalancer loadBalancer = loadBalancerConfig.getOrCreateLoadBalancerFor(node);
        reconditionLoadBalancer(loadBalancer);
    }

    private void reconditionBalancedUpstream(Cluster cluster) {
        reconditionLoadBalancer(balancedUpstreamFor(cluster));
        containerStatus.forEach(missing -> {
            LoadBalancer loadBalancer = balancedUpstreamFor(cluster);
            if (!loadBalancer.endpoints().contains(missing)) {
                loadBalancer.addEndpoint(missing);
            }
        });
    }

    private LoadBalancer balancedUpstreamFor(Cluster cluster) {
        return loadBalancerConfig.getOrCreateLoadBalancerFor(cluster);
    }

    private void reconditionLoadBalancer(LoadBalancer loadBalancer) {
        // copy, as we change the HostPort, which would cause ConcurrentModification
        for (Endpoint hostPort : new ArrayList<>(loadBalancer.endpoints())) {
            Integer actualPort = containerStatus.actualPort(hostPort.getHost());
            if (actualPort == null)
                continue; // TODO
            if (hostPort.getPort() != actualPort) {
                loadBalancer.updatePort(hostPort, actualPort);
            }
        }
    }

    @RequiredArgsConstructor
    private class NodeCleanup implements Runnable {
        private final ClusterNode node;
        private boolean lookForMore = false;

        @Override public void run() {
            Integer port = containerStatus.actualPort(node.host());
            if (port != null) {
                containerStatus.stop(node.endpoint().withPort(port));
                lookForMore = true;
            }
            if (loadBalancerConfig.hasLoadBalancerFor(node)) {
                lookForMore = true;
                loadBalancerConfig.removeUpstream(node.endpoint());
            }
            if (balancedUpstreamFor(node.getCluster()).hasHost(node.endpoint().getHost())) {
                lookForMore = true;
                balancedUpstreamFor(node.getCluster()).removeHost(node.endpoint().getHost());
            }

            if (lookForMore) {
                new NodeCleanup(node.next()).run();
            }
        }
    }
}
