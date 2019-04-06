package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.boundary.gateway.loadbalancer.Ingress;
import com.github.t1.kubee.boundary.gateway.loadbalancer.Ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.loadbalancer.Ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor
public class ClusterReconditioner implements Runnable {
    private final Map<Cluster, ClusterStatus> clusters;
    private final Ingress ingress;

    @Override public void run() {
        clusters.forEach(this::reconditionCluster);

        if (ingress.hasChanged()) {
            ingress.write();
        }
    }

    private void reconditionCluster(Cluster cluster, ClusterStatus clusterStatus) {
        cluster.stages().forEach(clusterStatus::scale);
        cluster.nodes().forEach(node -> reconditionReverseProxy(node, clusterStatus));
        cluster.lastNodes().forEach(node -> new NodeCleanup(clusterStatus, node.next()).run());
        reconditionLoadBalancers(clusterStatus);
    }

    private void reconditionReverseProxy(ClusterNode node, ClusterStatus clusterStatus) {
        Integer actualPort = clusterStatus.port(node.endpoint().getHost());
        if (actualPort == null)
            throw new IllegalStateException("expected " + node + " to be running");
        ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
        if (reverseProxy.getPort() != actualPort) {
            reverseProxy.setPort(actualPort);
        }
    }

    private void reconditionLoadBalancers(ClusterStatus clusterStatus) {
        ingress.loadBalancers().forEach(loadBalancer -> {
            reconditionLoadBalancer(loadBalancer, clusterStatus);
            clusterStatus.endpoints()
                .filter(actualEndpoint -> !loadBalancer.hasEndpoint(actualEndpoint))
                .forEach(loadBalancer::addOrUpdateEndpoint);
        });
    }

    private void reconditionLoadBalancer(LoadBalancer loadBalancer, ClusterStatus clusterStatus) {
        for (Endpoint hostPort : loadBalancer.getEndpoints()) {
            Integer actualPort = clusterStatus.port(hostPort.getHost());
            if (actualPort == null)
                continue; // TODO
            if (hostPort.getPort() != actualPort) {
                loadBalancer.updatePort(hostPort, actualPort);
            }
        }
    }

    @RequiredArgsConstructor
    private class NodeCleanup implements Runnable {
        private final ClusterStatus clusterStatus;
        private final ClusterNode node;
        private boolean lookForMore = false;

        @Override public void run() {
            if (ingress.hasReverseProxyFor(node)) {
                lookForMore = true;
                ingress.removeReverseProxyFor(node);
            }

            ingress.loadBalancers().forEach(loadBalancer -> {
                String host = node.endpoint().getHost();
                if (loadBalancer.hasHost(host)) {
                    lookForMore = true;
                    loadBalancer.removeHost(host);
                }
            });

            if (lookForMore) {
                new NodeCleanup(clusterStatus, node.next()).run();
            }
        }
    }
}
