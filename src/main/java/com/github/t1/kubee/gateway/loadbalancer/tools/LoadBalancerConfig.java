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
import java.util.Optional;
import java.util.function.Consumer;

class LoadBalancerConfig {
    private final NginxConfig nginxConfig;
    private final Consumer<String> note;

    private final Path configPath;
    private final String original;

    LoadBalancerConfig(Path configPath, Consumer<String> note) {
        this.configPath = configPath;
        this.note = note;

        this.nginxConfig = NginxConfig.readFrom(configPath.toUri());
        this.original = nginxConfig.toString();
    }

    boolean hasChanged() { return !nginxConfig.toString().equals(original); }

    void apply() { nginxConfig.writeTo(configPath); }

    public Optional<NginxServer> server(HostPort hostPort) {
        return nginxConfig.server(hostPort);
    }

    Optional<NginxUpstream> upstream(String host) {
        return nginxConfig.upstream(host);
    }

    void addUpstream(NginxUpstream upstream) {
        nginxConfig.addUpstream(upstream);
    }

    LoadBalancerConfig removeUpstream(String host) {
        nginxConfig.removeUpstream(host);
        return this;
    }

    void removeServer(HostPort hostPort) {
        nginxConfig.removeServer(hostPort);
    }

    LoadBalancer getOrCreateLoadBalancerFor(ClusterNode node) {
        LoadBalancer loadBalancer = new LoadBalancer(node.host(), node.port(), node.host(), "");
        loadBalancer.upstream().addHostPort(node.hostPort().toHostPort());
        return loadBalancer;
    }

    LoadBalancer getOrCreateLoadBalancerFor(Cluster cluster) {
        return new LoadBalancer("~^(?<app>.+).kub-ee$", cluster.getSlot().getHttp(), cluster.getSimpleName() + "_nodes", "$app");
    }

    @RequiredArgsConstructor
    class LoadBalancer {
        private final String fromPattern;
        private final int fromListen;
        private final String upstreamName;
        private final String upstreamPath;

        NginxUpstream upstream() {
            NginxServer server = nginxConfig.servers()
                .filter(it -> it.getName().equals(fromPattern) && it.getListen() == fromListen)
                .findAny().orElseGet(() -> {
                    note.accept("Create missing LB server: " + fromPattern);
                    NginxServer newServer = NginxServer.named(fromPattern).setListen(fromListen);
                    nginxConfig.addServer(newServer);
                    return newServer;
                });
            server.location("/").orElseGet(() -> {
                note.accept("Create missing LB location '/' in server: " + upstreamName);
                NginxServerLocation newLocation = NginxServerLocation.named("/")
                    .setProxyPass(URI.create("http://" + upstreamName + "/" + upstreamPath))
                    .setAfter("proxy_set_header Host      $host;\n" +
                        "            proxy_set_header X-Real-IP $remote_addr;");
                server.addLocation(newLocation);
                return newLocation;
            });
            return nginxConfig.upstream(upstreamName).orElseGet(() -> {
                note.accept("Create missing LB upstream: " + upstreamName);
                NginxUpstream newUpstream = NginxUpstream
                    .named(upstreamName)
                    .setMethod("least_conn");
                nginxConfig.addUpstream(newUpstream);
                return newUpstream;
            });
        }
    }
}
