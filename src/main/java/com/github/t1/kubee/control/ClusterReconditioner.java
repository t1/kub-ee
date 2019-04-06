package com.github.t1.kubee.control;

import com.github.t1.kubee.gateway.container.ContainerStatus;
import com.github.t1.kubee.gateway.loadbalancer.Ingress;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.LoadBalancer;
import com.github.t1.kubee.gateway.loadbalancer.Ingress.ReverseProxy;
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
@RequiredArgsConstructor
public class ClusterReconditioner implements Runnable {
    private final Consumer<String> note;
    private final List<Cluster> clusters;
    private final Path dockerComposeConfigPath;
    private final Ingress ingress;

    private ContainerStatus containerStatus;

    @Override public void run() {
        clusters.forEach(this::reconditionCluster);

        if (ingress.hasChanged()) {
            ingress.write();
        }
    }

    private void reconditionCluster(Cluster cluster) {
        this.containerStatus = new ContainerStatus(note, cluster, dockerComposeConfigPath);
        cluster.nodes().forEach(this::reconditionReverseProxy);
        cluster.lastNodes().forEach(node -> new NodeCleanup(node.next()).run());
        reconditionLoadBalancers();
    }

    private void reconditionReverseProxy(ClusterNode node) {
        Integer actualPort = containerStatus.actualPort(node.endpoint().getHost());
        if (actualPort == null) {
            actualPort = containerStatus.start(node);
        }
        ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
        if (reverseProxy.getPort() != actualPort) {
            reverseProxy.setPort(actualPort);
        }
    }

    private void reconditionLoadBalancers() {
        ingress.loadBalancers().forEach(loadBalancer -> {
            reconditionLoadBalancer(loadBalancer);
            containerStatus.actual()
                .filter(actualEndpoint -> !loadBalancer.hasEndpoint(actualEndpoint))
                .forEach(loadBalancer::addOrUpdateEndpoint);
        });
    }

    private void reconditionLoadBalancer(LoadBalancer loadBalancer) {
        for (Endpoint hostPort : loadBalancer.getEndpoints()) {
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
                new NodeCleanup(node.next()).run();
            }
        }
    }
}
