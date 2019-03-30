package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.util.List;

@RequiredArgsConstructor class ClusterUpdater implements Runnable {
    private final List<Cluster> clusterConfig;
    private final List<HostPort> dockerStatus;
    private final Path nginxConfigPath;
    private NginxConfig nginxConfig;

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
        NginxServer server = nginxConfig.server(node.hostPort()).orElseThrow(IllegalStateException::new);
        NginxServerLocation location = server.location("/").orElseThrow(IllegalStateException::new);
        return nginxConfig.upstream(location.getProxyPass().getHost()).orElseThrow(IllegalStateException::new);
    }

    private void handleBalancedUpstream() {
        clusterConfig.stream()
            .map(this::balancedUpstreamFor)
            .forEach(this::handleUpstream);
    }

    private NginxUpstream balancedUpstreamFor(Cluster cluster) {
        return nginxConfig.upstream(cluster.getSimpleName() + "_nodes").orElseThrow(IllegalStateException::new);
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
        System.out.println("Inconsistent port: " + expected.getHost() + ": " + expected.getPort() + " -> " + actualPort);
        nginxConfig = nginxConfig.withUpstream(upstreamName, upstream -> upstream.map(hostPort ->
            (hostPort.getHost().equals(expected.getHost())) ? hostPort.withPort(actualPort) : hostPort));
    }
}
