package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.clusters.ClusterStore;
import com.github.t1.kubee.boundary.gateway.container.ClusterStatus;
import com.github.t1.kubee.boundary.gateway.container.ClusterStatusGateway;
import com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress;
import com.github.t1.kubee.boundary.gateway.ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.DeploymentStatus;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;

import javax.inject.Inject;
import java.util.stream.Stream;

import static com.github.t1.kubee.boundary.gateway.ingress.IngressFactory.ingress;
import static com.github.t1.kubee.entity.DeploymentStatus.running;
import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static java.util.stream.Collectors.toList;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@Log
@AllArgsConstructor
@NoArgsConstructor
public class ClusterReconditioner implements Runnable {
    @Inject ClusterStore clusterStore;
    @Inject DeployerGateway deployerGateway;
    @Inject ClusterStatusGateway clusterStatusGateway;

    @Override public void run() { clusterStore.clusters().forEach(this::reconditionCluster); }

    private void reconditionCluster(Cluster cluster) {
        log.fine("reconditioning of cluster '" + cluster.getHost() + "' start");
        ClusterStatus clusterStatus = clusterStatusGateway.clusterStatus(cluster);
        cluster.stages()
            .map(stage -> new StageReconditioner(cluster, stage, clusterStatus))
            .forEach(StageReconditioner::recondition);
        log.fine("reconditioning of cluster '" + cluster.getHost() + "' done");
    }

    private class StageReconditioner {
        private final Cluster cluster;
        private final Stage stage;
        private final ClusterStatus clusterStatus;
        private final Ingress ingress;

        StageReconditioner(Cluster cluster, Stage stage, ClusterStatus clusterStatus) {
            this.cluster = cluster;
            this.clusterStatus = clusterStatus;
            this.stage = stage;

            this.ingress = ingress(stage);
        }

        private String what() { return "[" + cluster.getHost() + ":" + stage.getName() + "]"; }

        void recondition() {
            log.fine("scale " + what());
            clusterStatus.scale(stage);

            log.fine("recondition reverse proxies for " + what());
            nodes().forEach(this::reconditionReverseProxy);

            log.fine("recondition load balancers for " + what());
            ingress.loadBalancers().forEach(this::reconditionLoadBalancer);

            log.fine("cleanup ingress for " + what());
            cleanupNext(stage.lastNodeIn(cluster));

            if (ingress.hasChanged()) {
                log.fine("apply ingress for [" + cluster.getHost() + "]");
                ingress.apply();
            }
        }

        private Stream<ClusterNode> nodes() {
            return cluster.nodes().filter(stage -> stage.getStage().equals(this.stage));
        }

        private void reconditionReverseProxy(ClusterNode node) {
            log.fine("recondition reverse proxy for [" + node + "]");
            if (!"docker-compose".equals(node.getStage().getProvider())) {
                log.warning("unsupported provider '" + node.getStage().getProvider() + "' "
                    + "for stage '" + node.getStage().getName() + "' "
                    + "in cluster '" + node.getCluster().getSimpleName() + "." + node.getCluster().getDomainName());
                return;
            }
            Integer actualPort = clusterStatus.exposedPort(stage, node.host());
            if (actualPort == null)
                throw new IllegalStateException("expected " + node + " to be running");
            ReverseProxy reverseProxy = ingress.getOrCreateReverseProxyFor(node);
            if (reverseProxy.getPort() == actualPort)
                log.fine("reverse proxy for [" + node + "] already on correct port: " + actualPort);
            else
                reverseProxy.setPort(actualPort);
        }

        private void reconditionLoadBalancer(LoadBalancer loadBalancer) {
            log.fine("recondition load balancer for '" + loadBalancer.applicationName() + "' on " + what());
            new LoadBalancerReconditioner(loadBalancer).recondition();
        }

        private void cleanupNext(ClusterNode node) {
            ClusterNode next = stage.nodeAt(cluster, (node == null) ? 1 : node.getNumber() + 1);
            boolean lookForMore = false;

            if (ingress.hasReverseProxyFor(next)) {
                lookForMore = true;
                ingress.removeReverseProxyFor(next);
            }

            for (LoadBalancer loadBalancer : ingress.loadBalancers().collect(toList())) {
                String host = next.endpoint().getHost();
                if (loadBalancer.hasHost(host)) {
                    lookForMore = true;
                    loadBalancer.removeHost(host);
                }
            }

            if (lookForMore) {
                cleanupNext(next);
            }
        }

        @RequiredArgsConstructor
        private class LoadBalancerReconditioner {
            private final LoadBalancer loadBalancer;

            void recondition() {
                loadBalancer.endpoints().forEach(endpoint -> {
                    String host = endpoint.getHost();
                    if (getConfiguredDeploymentStatus(host) == unbalanced) {
                        loadBalancer.removeHost(host);
                    } else {
                        Integer actualPort = clusterStatus.exposedPort(stage, host);
                        if (actualPort == null)
                            return; // will be removed in cleanup
                        if (endpoint.getPort() != actualPort) {
                            loadBalancer.updatePort(endpoint, actualPort);
                        }
                    }
                });
                clusterStatus.endpoints()
                    .filter(this::needsEndpoint)
                    .forEach(loadBalancer::addOrUpdateEndpoint);
            }

            private boolean needsEndpoint(Endpoint endpoint) {
                return !loadBalancer.hasEndpoint(endpoint)
                    && getConfiguredDeploymentStatus(endpoint.getHost()) == running
                    && isDeployed(endpoint);
            }

            private DeploymentStatus getConfiguredDeploymentStatus(String host) {
                ClusterNode node = findNode(host);
                return (node == null) ? null : node.getStatusOfApp(loadBalancer.applicationName());
            }

            private ClusterNode findNode(String host) {
                return cluster.nodes().filter(n -> n.endpoint().getHost().equals(host)).findAny().orElse(null);
            }

            private boolean isDeployed(Endpoint endpoint) {
                if (deployerGateway == null)
                    return true; // don't know better
                String version = deployerGateway.fetchVersion(findNode(endpoint.getHost()), loadBalancer.applicationName());
                return version != null;
            }
        }
    }
}
