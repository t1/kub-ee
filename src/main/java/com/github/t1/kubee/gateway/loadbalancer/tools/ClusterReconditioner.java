package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.gateway.loadbalancer.tools.LoadBalancerConfig.LoadBalancer;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.ArrayList;
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
        Integer actualPort = containerStatus.actualPort(node.hostPort().getHost());
        if (actualPort == null) {
            containerStatus.start(node);
        }
        LoadBalancer loadBalancer = loadBalancerConfig.getOrCreateLoadBalancerFor(node);
        reconditionLoadBalancer(loadBalancer);
    }

    private void reconditionBalancedUpstream(Cluster cluster) {
        reconditionLoadBalancer(balancedUpstreamFor(cluster));
        containerStatus.forEach(missing -> {
            NginxUpstream upstream = balancedUpstreamFor(cluster).upstream();
            if (!upstream.getHostPorts().contains(missing)) {
                note.accept("Add missing LB server " + missing + " to upstream " + upstream.getName());
                loadBalancerConfig
                    .removeUpstream(upstream.getName())
                    .addUpstream(upstream.addHostPort(missing));
            }
        });
    }

    private LoadBalancer balancedUpstreamFor(Cluster cluster) {
        return loadBalancerConfig.getOrCreateLoadBalancerFor(cluster);
    }

    private void reconditionLoadBalancer(LoadBalancer loadBalancer) {
        // copy, as we change the HostPort, which would cause ConcurrentModification
        for (HostPort hostPort : new ArrayList<>(loadBalancer.upstream().getHostPorts())) {
            Integer actualPort = containerStatus.actualPort(hostPort.getHost());
            if (actualPort == null)
                continue; // TODO
            if (hostPort.getPort() != actualPort) {
                updatePort(loadBalancer, hostPort, actualPort);
            }
        }
    }

    private void updatePort(LoadBalancer loadBalancer, HostPort hostPort, int actualPort) {
        note.accept("LB port doesn't match actual: " + hostPort.getHost() + ": " + hostPort.getPort() + " -> " + actualPort);
        loadBalancer.upstream().setPort(hostPort, actualPort);
    }

    @RequiredArgsConstructor
    private class NodeCleanup implements Runnable {
        private final ClusterNode node;
        private boolean lookForMore = false;

        @Override public void run() {
            Integer port = containerStatus.actualPort(node.host());
            if (port != null) {
                containerStatus.stop(node.hostPort().toHostPort().withPort(port));
                lookForMore = true;
            }
            loadBalancerConfig.upstream(node.host()).ifPresent(upstream -> {
                lookForMore = true;
                loadBalancerConfig.removeUpstream(node.host());
            });
            HostPort hostPort = node.hostPort().toHostPort();
            loadBalancerConfig.server(hostPort).ifPresent(server -> {
                lookForMore = true;
                loadBalancerConfig.removeServer(hostPort);
            });
            if (balancedUpstreamFor(node.getCluster()).upstream().hasHost(hostPort.getHost())) {
                lookForMore = true;
                balancedUpstreamFor(node.getCluster()).upstream().removeHost(hostPort.getHost());
            }

            if (lookForMore) {
                new NodeCleanup(node.next()).run();
            }
        }
    }
}
