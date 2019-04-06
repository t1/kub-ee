package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.container.Status;
import com.github.t1.kubee.gateway.loadbalancer.Ingress;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.LoadBalancer;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.ReverseProxy;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor
public class ClusterReconditioner implements Runnable {
    private final Map<Cluster, Status> clusters;
    private final Ingress ingress;

    @Override public void run() {
        clusters.forEach(this::reconditionCluster);

        if (ingress.hasChanged()) {
            ingress.write();
        }
    }

    private void reconditionCluster(Cluster cluster, Status status) {
        cluster.nodes().forEach(node -> reconditionReverseProxy(node, status));
        cluster.lastNodes().forEach(node -> new NodeCleanup(status, node.next()).run());
        reconditionLoadBalancers(status);
    }

    private void reconditionReverseProxy(ClusterNode node, Status status) {
        Integer actualPort = status.port(node.endpoint().getHost());
        if (actualPort == null) {
            actualPort = status.start(node);
        }
        ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
        if (reverseProxy.getPort() != actualPort) {
            reverseProxy.setPort(actualPort);
        }
    }

    private void reconditionLoadBalancers(Status status) {
        ingress.loadBalancers().forEach(loadBalancer -> {
            reconditionLoadBalancer(loadBalancer, status);
            status.endpoints()
                .filter(actualEndpoint -> !loadBalancer.hasEndpoint(actualEndpoint))
                .forEach(loadBalancer::addOrUpdateEndpoint);
        });
    }

    private void reconditionLoadBalancer(LoadBalancer loadBalancer, Status status) {
        for (Endpoint hostPort : loadBalancer.getEndpoints()) {
            Integer actualPort = status.port(hostPort.getHost());
            if (actualPort == null)
                continue; // TODO
            if (hostPort.getPort() != actualPort) {
                loadBalancer.updatePort(hostPort, actualPort);
            }
        }
    }

    @RequiredArgsConstructor
    private class NodeCleanup implements Runnable {
        private final Status status;
        private final ClusterNode node;
        private boolean lookForMore = false;

        @Override public void run() {
            Integer port = status.port(node.host());
            if (port != null) {
                status.stop(node.endpoint().withPort(port));
                lookForMore = true;
            }
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
                new NodeCleanup(status, node.next()).run();
            }
        }
    }
}
