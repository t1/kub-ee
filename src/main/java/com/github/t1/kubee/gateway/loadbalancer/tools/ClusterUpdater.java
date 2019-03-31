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
        NginxConfig originalNginxConfig = nginxConfig;
        handleLoadBalancer();
        if (!nginxConfig.equals(originalNginxConfig)) {
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
            NginxServer newServer = NginxServer.named(node.host()).withListen(node.port());
            nginxConfig = nginxConfig.withServer(newServer);
            return newServer;
        });
        NginxServerLocation location = server.location("/").orElseGet(() -> {
            note.accept("Create missing nginx location '/' in server: " + node.host());
            NginxServerLocation newLocation = NginxServerLocation.named("/")
                .withProxyPass(URI.create("http://" + node.host() + "/"))
                .withAfter("proxy_set_header Host      $host;\n" +
                    "            proxy_set_header X-Real-IP $remote_addr;");
            nginxConfig = nginxConfig.withoutServer(server.getName()).withServer(server.withLocation(newLocation));
            return newLocation;
        });
        return nginxConfig.upstream(location.getProxyPass().getHost()).orElseGet(() -> {
            note.accept("Create missing nginx upstream: " + node.host());
            NginxUpstream newUpstream = NginxUpstream
                .named(node.host())
                .withMethod("least_conn")
                .withHostPort(node.hostPort().toHostPort());
            nginxConfig = nginxConfig.withUpstream(newUpstream);
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
                    nginxConfig = nginxConfig
                        .withoutUpstream(upstream.getName())
                        .withUpstream(upstream.withHostPort(missing));
                }
            });
        }
    }

    private NginxUpstream balancedUpstreamFor(Cluster cluster) {
        return nginxConfig.upstream(cluster.getSimpleName() + "_nodes")
            .orElseThrow(IllegalStateException::new);
    }

    private void handleUpstream(NginxUpstream upstream) {
        upstream.getHostPorts().forEach(expected -> {
            int actualPort = getActualPort(expected.getHost());
            if (expected.getPort() != actualPort) {
                updatePort(upstream.getName(), expected, actualPort);
            }
        });
    }

    private int getActualPort(String host) {
        return dockerStatus.stream()
            .filter(hostPort -> hostPort.getHost().equals(host))
            .findFirst().orElseThrow(IllegalStateException::new)
            .getPort();
    }

    private void updatePort(String upstreamName, HostPort expected, int actualPort) {
        note.accept("Inconsistent port: " + expected.getHost() + ": " + expected.getPort() + " -> " + actualPort);
        nginxConfig = nginxConfig.withUpstream(upstreamName, upstream -> upstream.map(hostPort ->
            (hostPort.getHost().equals(expected.getHost())) ? hostPort.withPort(actualPort) : hostPort));
    }
}
