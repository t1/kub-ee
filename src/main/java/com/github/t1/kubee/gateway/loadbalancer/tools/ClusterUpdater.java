package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.RequiredArgsConstructor;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Takes the cluster-config and checks if the docker-compose is running as defined. If not, scale up or down as specified.
 * Then look at the load balancer config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor class ClusterUpdater implements Runnable {
    private final List<Cluster> clusterConfig;
    private final ContainerStatus containerStatus;
    private final Path lbConfigPath;
    private NginxConfig lbConfig;

    private final Consumer<String> note = System.out::println;

    @Override public void run() {
        lbConfig = NginxConfig.readFrom(lbConfigPath.toUri());
        String originalLbConfig = lbConfig.toString();
        handleLoadBalancer();
        if (!lbConfig.toString().equals(originalLbConfig)) {
            lbConfig.writeTo(lbConfigPath);
        }
    }

    private void handleLoadBalancer() {
        clusterConfig.forEach(this::handleCluster);
    }

    private void handleCluster(Cluster cluster) {
        cluster.nodes().forEach(this::handleNode);
        cluster.lastNodes().forEach(node -> checkExcessNodes(node.next()));
        handleBalancedUpstream(cluster);
    }

    private void handleNode(ClusterNode node) {
        Integer actualPort = containerStatus.actualPort(node.hostPort().getHost());
        if (actualPort == null) {
            note.accept("Start missing container " + node.hostPort());
            containerStatus.start(node);
        }
        handleUpstream(nodeUpstreamFor(node));
    }

    private NginxUpstream nodeUpstreamFor(ClusterNode node) {
        NginxServer server = lbConfig.server(node.hostPort().toHostPort()).orElseGet(() -> {
            note.accept("Create missing LB server: " + node.host());
            NginxServer newServer = NginxServer.named(node.host()).setListen(node.port());
            lbConfig.addServer(newServer);
            return newServer;
        });
        NginxServerLocation location = server.location("/").orElseGet(() -> {
            note.accept("Create missing LB location '/' in server: " + node.host());
            NginxServerLocation newLocation = NginxServerLocation.named("/")
                .setProxyPass(URI.create("http://" + node.host() + "/"))
                .setAfter("proxy_set_header Host      $host;\n" +
                    "            proxy_set_header X-Real-IP $remote_addr;");
            server.addLocation(newLocation);
            return newLocation;
        });
        return lbConfig.upstream(location.getProxyPass().getHost()).orElseGet(() -> {
            note.accept("Create missing LB upstream: " + node.host());
            NginxUpstream newUpstream = NginxUpstream
                .named(node.host())
                .setMethod("least_conn")
                .addHostPort(node.hostPort().toHostPort());
            lbConfig.addUpstream(newUpstream);
            return newUpstream;
        });
    }

    private void checkExcessNodes(ClusterNode node) {
        boolean[] lookForMore = {false};
        Integer port = containerStatus.actualPort(node.host());
        if (port != null) {
            stopContainer(node.hostPort().toHostPort().withPort(port));
            lookForMore[0] = true;
        }
        lbConfig.upstream(node.host()).ifPresent(upstream -> {
            lookForMore[0] = true;
            lbConfig.removeUpstream(node.host());
        });
        HostPort hostPort = node.hostPort().toHostPort();
        lbConfig.server(hostPort).ifPresent(server -> {
            lookForMore[0] = true;
            lbConfig.removeServer(hostPort);
        });
        if (balancedUpstreamFor(node.getCluster()).hasHost(hostPort.getHost())) {
            lookForMore[0] = true;
            balancedUpstreamFor(node.getCluster()).removeHost(hostPort.getHost());
        }

        if (lookForMore[0]) {
            checkExcessNodes(node.next());
        }
    }

    private void stopContainer(HostPort hostPort) {
        note.accept("Stopping excess container " + hostPort);
    }

    private void handleBalancedUpstream(Cluster cluster) {
        handleUpstream(balancedUpstreamFor(cluster));
        containerStatus.forEach(missing -> {
            NginxUpstream upstream = balancedUpstreamFor(cluster);
            if (!upstream.getHostPorts().contains(missing)) {
                note.accept("Add missing LB server " + missing + " to upstream " + upstream.getName());
                lbConfig
                    .removeUpstream(upstream.getName())
                    .addUpstream(upstream.addHostPort(missing));
            }
        });
    }

    private NginxUpstream balancedUpstreamFor(Cluster cluster) {
        return lbConfig.upstream(cluster.getSimpleName() + "_nodes")
            .orElseThrow(IllegalStateException::new);
    }

    private void handleUpstream(NginxUpstream upstream) {
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
