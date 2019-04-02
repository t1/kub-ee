package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.gateway.loadbalancer.tools.LoadBalancerConfig.LoadBalancer;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale it up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor class ClusterReconditioner implements Runnable {
    private final List<Cluster> clusterConfig;
    private final ContainerStatus containerStatus;
    private final Path loadBalancerPath;
    private LoadBalancerConfig loadBalancerConfig;

    private final Consumer<String> note = System.out::println;

    @Override public void run() {
        loadBalancerConfig = new LoadBalancerConfig(loadBalancerPath, note);

        clusterConfig.forEach(this::reconditionCluster);

        if (loadBalancerConfig.hasChanged()) {
            loadBalancerConfig.apply();
        }
    }

    private void reconditionCluster(Cluster cluster) {
        cluster.nodes().forEach(this::reconditionNode);
        cluster.lastNodes().forEach(node -> removeExcessNodes(node.next()));
        reconditionBalancedUpstream(cluster);
    }

    private void reconditionNode(ClusterNode node) {
        Integer actualPort = containerStatus.actualPort(node.hostPort().getHost());
        if (actualPort == null) {
            note.accept("Start missing container " + node.hostPort());
            containerStatus.start(node);
        }
        LoadBalancer loadBalancer = loadBalancerConfig.getOrCreateLoadBalancerFor(node);
        reconditionLoadBalancer(loadBalancer.upstream());
    }

    private void removeExcessNodes(ClusterNode node) {
        boolean[] lookForMore = {false};
        Integer port = containerStatus.actualPort(node.host());
        if (port != null) {
            stopContainer(node.hostPort().toHostPort().withPort(port));
            lookForMore[0] = true;
        }
        loadBalancerConfig.upstream(node.host()).ifPresent(upstream -> {
            lookForMore[0] = true;
            loadBalancerConfig.removeUpstream(node.host());
        });
        HostPort hostPort = node.hostPort().toHostPort();
        loadBalancerConfig.server(hostPort).ifPresent(server -> {
            lookForMore[0] = true;
            loadBalancerConfig.removeServer(hostPort);
        });
        if (balancedUpstreamFor(node.getCluster()).hasHost(hostPort.getHost())) {
            lookForMore[0] = true;
            balancedUpstreamFor(node.getCluster()).removeHost(hostPort.getHost());
        }

        if (lookForMore[0]) {
            removeExcessNodes(node.next());
        }
    }

    private void stopContainer(HostPort hostPort) {
        note.accept("Stopping excess container " + hostPort);
    }

    private void reconditionBalancedUpstream(Cluster cluster) {
        reconditionLoadBalancer(balancedUpstreamFor(cluster));
        containerStatus.forEach(missing -> {
            NginxUpstream upstream = balancedUpstreamFor(cluster);
            if (!upstream.getHostPorts().contains(missing)) {
                note.accept("Add missing LB server " + missing + " to upstream " + upstream.getName());
                loadBalancerConfig
                    .removeUpstream(upstream.getName())
                    .addUpstream(upstream.addHostPort(missing));
            }
        });
    }

    private NginxUpstream balancedUpstreamFor(Cluster cluster) {
        return loadBalancerConfig.getOrCreateLoadBalancerFor(cluster).upstream();
    }

    private void reconditionLoadBalancer(NginxUpstream upstream) {
        // no enhanced for loop or stream, as we change the HostPort, which would cause ConcurrentModification
        for (int i = 0; i < upstream.getHostPorts().size(); i++) {
            HostPort hostPort = upstream.getHostPorts().get(i);
            Integer actualPort = containerStatus.actualPort(hostPort.getHost());
            if (actualPort == null)
                continue; // TODO
            if (hostPort.getPort() != actualPort) {
                updatePort(upstream, hostPort, actualPort);
            }
        }
    }

    private void updatePort(NginxUpstream upstream, HostPort hostPort, int actualPort) {
        note.accept("LB port doesn't match actual: " + hostPort.getHost() + ": " + hostPort.getPort() + " -> " + actualPort);
        upstream.setPort(hostPort, actualPort);
    }
}
