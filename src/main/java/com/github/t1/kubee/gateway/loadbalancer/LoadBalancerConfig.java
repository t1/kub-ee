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

/**
 * We have two use cases:
 * <ul>
 * <li><b>Load Balancer</b>: a set of nodes acting as a cluster for an application.</li>
 * <li><b>Reverse Proxy</b>: a single node in a cluster exposed to the exterior.</li>
 * </ul>
 */
public class LoadBalancerConfig {
    private static final String LOAD_BALANCER_SERVER_PATTERN = "~^(?<app>.+).kub-ee$";
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

    public void removeReverseProxyFor(ClusterNode node) {
        nginxConfig.removeServer(new HostPort(node.host(), node.port()));
        nginxConfig.removeUpstream(node.host());
    }

    public boolean hasReverseProxyFor(ClusterNode node) {
        return nginxConfig.upstream(node.host()).isPresent();
    }

    public ReverseProxy getOrCreateReverseProxyFor(ClusterNode node) {
        return new ReverseProxy(node.host(), node.port());
    }

    public class ReverseProxy {
        private final NginxServer server;
        private final NginxUpstream upstream;

        ReverseProxy(String name, int fromPort) {
            this.server = getOrCreateServer(name, fromPort, name, "");
            this.upstream = getOrCreateUpstream(name);
        }

        public int getPort() {
            List<HostPort> hostPorts = upstream.getHostPorts();
            if (hostPorts.isEmpty())
                return -1;
            if (hostPorts.size() > 1)
                throw new IllegalStateException("expected exactly one endpoint in reverse proxy " + upstream.getName() + " but got " + hostPorts);
            return hostPorts.get(0).getPort();
        }

        public void setPort(int port) {
            note.accept("Add port of ReverseProxy " + upstream.getName() + " to " + port);
            List<HostPort> hostPorts = upstream.getHostPorts();
            if (hostPorts.size() > 1)
                throw new IllegalStateException("expected no more than one endpoint in reverse proxy " + upstream.getName() + " but got " + hostPorts);
            if (hostPorts.isEmpty()) {
                hostPorts.add(new HostPort(upstream.getName(), port));
            } else {
                hostPorts.set(0, hostPorts.get(0).withPort(port));
            }
        }
    }


    public boolean hasLoadBalancerFor(Cluster cluster) {
        return nginxConfig.server(LOAD_BALANCER_SERVER_PATTERN, cluster.getSlot().getHttp()).isPresent();
    }

    public LoadBalancer getOrCreateLoadBalancerFor(Cluster cluster) { // TODO and application name
        return new LoadBalancer(LOAD_BALANCER_SERVER_PATTERN, cluster.getSlot().getHttp(), cluster.getSimpleName() + "_nodes", "$app");
    }

    public class LoadBalancer {
        private final NginxServer server;
        private final NginxUpstream upstream;

        LoadBalancer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
            this.server = getOrCreateServer(fromPattern, fromListen, upstreamName, upstreamPath);
            this.upstream = getOrCreateUpstream(upstreamName);
        }

        public void updatePort(Endpoint endpoint, Integer newPort) {
            note.accept("LB port doesn't match actual: " + endpoint + " -> " + newPort);
            upstream.setPort(toHostPort(endpoint), newPort);
        }

        public boolean hasHost(String host) { return upstream.hasHost(host); }

        public void removeHost(String host) { upstream.removeHost(host); }


        public boolean hasEndpoint(Endpoint endpoint) { return getEndpoints().contains(endpoint); }

        public List<Endpoint> getEndpoints() {
            return upstream.hostPorts().map(Tools::toEndpoint).collect(toList());
        }

        public void addOrUpdateEndpoint(Endpoint endpoint) {
            if (upstream.hasHost(endpoint.getHost())) {
                note.accept("Update endpoint " + endpoint + " to LB " + upstream.getName());
                upstream.updateHostPort(toHostPort(endpoint));
            } else {
                note.accept("Add missing endpoint " + endpoint + " to LB " + upstream.getName());
                upstream.addHostPort(toHostPort(endpoint));
            }
        }
    }


    private NginxServer getOrCreateServer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
        NginxServer server = nginxConfig.server(fromPattern, fromListen).orElseGet(() -> {
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
        return server;
    }

    private NginxUpstream getOrCreateUpstream(String upstreamName) {
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
