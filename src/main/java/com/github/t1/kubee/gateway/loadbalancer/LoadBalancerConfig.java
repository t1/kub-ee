package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.tools.Tools;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.Tools.toHostPort;
import static java.util.stream.Collectors.toList;

public class LoadBalancerConfig {
    private final NginxConfig nginxConfig;
    private final Consumer<String> note;

    private final Path configPath;
    private final String original;

    public LoadBalancerConfig(Path configPath, Consumer<String> note) {
        this.configPath = configPath;
        this.note = note;

        this.nginxConfig = NginxConfig.readFrom(configPath.toUri());
        this.original = nginxConfig.toString();
    }

    LoadBalancerConfig(NginxConfig nginxConfig, Consumer<String> note) {
        this.configPath = null;
        this.note = note;
        this.nginxConfig = nginxConfig;
        this.original = nginxConfig.toString();
    }

    public boolean hasChanged() { return !nginxConfig.toString().equals(original); }

    public void apply() { nginxConfig.writeTo(configPath); }

    public void removeUpstream(Endpoint endpoint) {
        nginxConfig.removeServer(toHostPort(endpoint));
        nginxConfig.removeUpstream(endpoint.getHost());
    }

    public boolean hasLoadBalancerFor(ClusterNode node) {
        return nginxConfig.upstream(node.host()).isPresent();
    }

    public LoadBalancer getOrCreateLoadBalancerFor(ClusterNode node) {
        LoadBalancer loadBalancer = new LoadBalancer(node.host(), node.port(), node.host(), "");
        loadBalancer.upstream.updateHostPort(toHostPort(node.endpoint()));
        return loadBalancer;
    }

    public LoadBalancer getOrCreateLoadBalancerFor(Cluster cluster) {
        return new LoadBalancer("~^(?<app>.+).kub-ee$", cluster.getSlot().getHttp(), cluster.getSimpleName() + "_nodes", "$app");
    }

    public class LoadBalancer {
        private final NginxServer server;
        private final NginxUpstream upstream;

        LoadBalancer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
            this.server = nginxConfig.server(toHostPort(new Endpoint(fromPattern, fromListen))).orElseGet(() -> {
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
            this.upstream = nginxConfig.upstream(upstreamName).orElseGet(() -> {
                note.accept("Create missing LB upstream: " + upstreamName);
                NginxUpstream newUpstream = NginxUpstream
                    .named(upstreamName)
                    .setMethod("least_conn");
                nginxConfig.addUpstream(newUpstream);
                return newUpstream;
            });
        }

        public void updatePort(Endpoint endpoint, Integer newPort) {
            note.accept("LB port doesn't match actual: " + endpoint + " -> " + newPort);
            upstream.setPort(toHostPort(endpoint), newPort);
        }

        public List<Endpoint> endpoints() {
            return upstream.getHostPorts().stream().map(it -> new Endpoint(it.getHost(), it.getPort())).collect(toList());
        }

        public void addEndpoint(Endpoint endpoint) {
            note.accept("Add missing LB server " + endpoint + " to upstream " + upstream.getName());
            upstream.addHostPort(toHostPort(endpoint));
        }

        public boolean hasHost(String host) { return upstream.hasHost(host); }

        public void removeHost(String host) { upstream.removeHost(host); }
    }
}
