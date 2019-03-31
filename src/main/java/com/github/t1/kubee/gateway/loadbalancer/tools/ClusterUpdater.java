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
 * Then look at the nginx config, and update it as specified in the (updated) docker-compose file.
 */
@RequiredArgsConstructor class ClusterUpdater implements Runnable {
    private final List<Cluster> clusterConfig;
    private final List<HostPort> dockerStatus;
    private final Path nginxConfigPath;
    private NginxConfig nginxConfig;

    private final Consumer<String> note = System.out::println;

    @Override public void run() {
        nginxConfig = NginxConfig.readFrom(nginxConfigPath.toUri());
        String originalNginxConfig = nginxConfig.toString();
        handleLoadBalancer();
        if (!nginxConfig.toString().equals(originalNginxConfig)) {
            nginxConfig.writeTo(nginxConfigPath);
        }
    }

    private void handleLoadBalancer() {
        handleNodeUpstreams();
        handleBalancedUpstream();
    }

    private void handleNodeUpstreams() {
        clusterConfig.stream()
            .flatMap(Cluster::nodes)
            .map(this::nodeUpstreamFor)
            .forEach(this::handleUpstream);
    }

    private NginxUpstream nodeUpstreamFor(ClusterNode node) {
        NginxServer server = nginxConfig.server(node.hostPort().toHostPort()).orElseGet(() -> {
            note.accept("Create missing nginx server: " + node.host());
            NginxServer newServer = NginxServer.named(node.host()).setListen(node.port());
            nginxConfig.addServer(newServer);
            return newServer;
        });
        NginxServerLocation location = server.location("/").orElseGet(() -> {
            note.accept("Create missing nginx location '/' in server: " + node.host());
            NginxServerLocation newLocation = NginxServerLocation.named("/")
                .setProxyPass(URI.create("http://" + node.host() + "/"))
                .setAfter("proxy_set_header Host      $host;\n" +
                    "            proxy_set_header X-Real-IP $remote_addr;");
            nginxConfig.removeServer(server.getName()).addServer(server.addLocation(newLocation));
            return newLocation;
        });
        return nginxConfig.upstream(location.getProxyPass().getHost()).orElseGet(() -> {
            note.accept("Create missing nginx upstream: " + node.host());
            NginxUpstream newUpstream = NginxUpstream
                .named(node.host())
                .setMethod("least_conn")
                .addHostPort(node.hostPort().toHostPort());
            nginxConfig.addUpstream(newUpstream);
            return newUpstream;
        });
    }

    private void handleBalancedUpstream() {
        for (Cluster cluster : clusterConfig) {
            handleUpstream(balancedUpstreamFor(cluster));
            dockerStatus.forEach(missing -> {
                NginxUpstream upstream = balancedUpstreamFor(cluster);
                if (!upstream.getHostPorts().contains(missing)) {
                    note.accept("Add missing server " + missing + " to upstream " + upstream.getName());
                    nginxConfig
                        .removeUpstream(upstream.getName())
                        .addUpstream(upstream.addHostPort(missing));
                }
            });
        }
    }

    private NginxUpstream balancedUpstreamFor(Cluster cluster) {
        return nginxConfig.upstream(cluster.getSimpleName() + "_nodes")
            .orElseThrow(IllegalStateException::new);
    }

    private void handleUpstream(NginxUpstream upstream) {
        // no enhanced for loop or stream, as we change the port, which would cause ConcurrentModification
        for (int i = 0; i < upstream.getHostPorts().size(); i++) {
            HostPort expected = upstream.getHostPorts().get(i);
            int actualPort = getActualPort(expected.getHost());
            if (expected.getPort() != actualPort) {
                updatePort(upstream, expected, actualPort);
            }
        }
    }

    private int getActualPort(String host) {
        return dockerStatus.stream()
            .filter(hostPort -> hostPort.getHost().equals(host))
            .findFirst().orElseThrow(IllegalStateException::new)
            .getPort();
    }

    private void updatePort(NginxUpstream upstream, HostPort hostPort, int actualPort) {
        note.accept("Inconsistent port: " + hostPort.getHost() + ": " + hostPort.getPort() + " -> " + actualPort);
        upstream.setPort(hostPort, actualPort);
    }
}
