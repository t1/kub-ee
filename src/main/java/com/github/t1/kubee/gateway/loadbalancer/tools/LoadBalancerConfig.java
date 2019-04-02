package com.github.t1.kubee.gateway.loadbalancer.tools;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;

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

    void addServer(NginxServer server) {
        nginxConfig.addServer(server);
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
        return new LoadBalancer(node.hostPort().toHostPort());
    }

    class LoadBalancer {
        final NginxUpstream upstream;

        private LoadBalancer(HostPort hostPort) {
            NginxServer server = nginxConfig.server(hostPort).orElseGet(() -> {
                note.accept("Create missing LB server: " + hostPort.getHost());
                NginxServer newServer = NginxServer.named(hostPort.getHost()).setListen(hostPort.getPort());
                nginxConfig.addServer(newServer);
                return newServer;
            });
            NginxServerLocation location = server.location("/").orElseGet(() -> {
                note.accept("Create missing LB location '/' in server: " + hostPort.getHost());
                NginxServerLocation newLocation = NginxServerLocation.named("/")
                    .setProxyPass(URI.create("http://" + hostPort.getHost() + "/"))
                    .setAfter("proxy_set_header Host      $host;\n" +
                        "            proxy_set_header X-Real-IP $remote_addr;");
                server.addLocation(newLocation);
                return newLocation;
            });
            this.upstream = nginxConfig.upstream(location.getProxyPass().getHost()).orElseGet(() -> {
                note.accept("Create missing LB upstream: " + hostPort.getHost());
                NginxUpstream newUpstream = NginxUpstream
                    .named(hostPort.getHost())
                    .setMethod("least_conn")
                    .addHostPort(hostPort);
                nginxConfig.addUpstream(newUpstream);
                return newUpstream;
            });
        }
    }
}
