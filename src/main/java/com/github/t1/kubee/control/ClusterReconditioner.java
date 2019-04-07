package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor
public class ClusterReconditioner implements Runnable {
    private final Map<Cluster, ClusterStatus> clusters;

    @Override public void run() {
        clusters.forEach(this::reconditionCluster);
    }

    private void reconditionCluster(Cluster cluster, ClusterStatus clusterStatus) {
        cluster.stages()
            .map(stage -> new StageReconditioner(cluster, stage, clusterStatus))
            .forEach(StageReconditioner::recondition);
    }

    private static class StageReconditioner {
        private final Cluster cluster;
        private final Stage stage;
        private final ClusterStatus clusterStatus;
        private final Ingress ingress;

        StageReconditioner(Cluster cluster, Stage stage, ClusterStatus clusterStatus) {
            this.cluster = cluster;
            this.clusterStatus = clusterStatus;
            this.stage = stage;

            this.ingress = Ingress.ingress(stage);
        }

        void recondition() {
            clusterStatus.scale(stage);
            cluster.nodes().forEach(this::reconditionReverseProxy);
            cluster.lastNodes().forEach(node -> new NodeCleanup(node.next()).run());
            reconditionLoadBalancers(clusterStatus);

            if (ingress.hasChanged()) {
                ingress.apply();
            }
        }

        private void reconditionReverseProxy(ClusterNode node) {
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
                    new NodeCleanup(node.next()).run();
                }
            }
        }
    }
}
