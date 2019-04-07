package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.Tools;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.Tools.toHostPort;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * We have two use cases:
 * <ul>
 * <li><b>Load Balancer</b>: a set of nodes acting as a cluster for an application.</li>
 * <li><b>Reverse Proxy</b>: a single node in a cluster exposed to the exterior.</li>
 * </ul>
 * <p>
 * The public interface is generic, the implementation is NGINX specific.
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Log
@NoArgsConstructor(force = true)
public class Ingress {
    @VisibleForTesting
    static Path NGINX_ETC = Paths.get("/usr/local/etc/nginx");

    public static Function<Stage, Ingress> BUILDER = Ingress::new;

    public static Ingress ingress(Stage stage) { return BUILDER.apply(stage); }

    private static String toString(Stream<ReverseProxy> reverseProxies) {
        return reverseProxies.map(ReverseProxy::name).collect(joining(", ", "[", "]"));
    }

    private final @NotNull Stage stage;
    private final @NotNull NginxConfig nginxConfig;

    private final @NotNull Path configPath;
    private final @NotNull String original;

    private Ingress(@NotNull Stage stage) {
        this.stage = stage;

        this.configPath = configPath();
        this.nginxConfig = NginxConfig.readFrom(configPath.toUri());
        this.original = nginxConfig.toString();
    }

    private Path configPath() {
        return NGINX_ETC.resolve(stage.getLoadBalancerConfig().getOrDefault("config-path",
            stage.getPrefix() + "nginx" + stage.getSuffix() + ".conf"));
    }

    public boolean hasChanged() { return !nginxConfig.toString().equals(original); }

    public void apply() {
        log.info("apply ingress config");
        writeConfig(nginxConfig.toString());

        log.info("reload ingress");
        reload();
    }

    @SneakyThrows(IOException.class) private void writeConfig(String string) {
        Files.write(configPath, string.getBytes());
    }

    private void reload() {
        String result = IngressReloader.reloadMode(stage).reload();
        if (result != null) {
            String message = "failed to reload load balancer: " + result;
            log.warning(message + ". restoring original config in " + configPath);
            writeConfig(original);
            throw new RuntimeException(message);
        }
    }

    public void removeReverseProxyFor(ClusterNode node) {
        nginxConfig.removeServer(new HostPort(node.host(), node.port()));
        nginxConfig.removeUpstream(node.host());
    }

    public boolean hasReverseProxyFor(ClusterNode node) {
        return nginxConfig.upstream(node.host()).isPresent();
    }

    public Stream<ReverseProxy> reverseProxies() {
        return nginxConfig.upstreams().filter(this::isReverseProxy).map(ReverseProxy::new);
    }

    private boolean isReverseProxy(NginxUpstream upstream) {
        return !upstream.getName().endsWith("-lb") && upstream.getHostPorts().size() == 1;
    }

    public void addToLoadBalancerFor(String application, ClusterNode node) {
        if (!hasReverseProxyFor(node))
            throw new IllegalStateException("no reverse proxy found for " + node.host() + " in " + toString(reverseProxies()));
        int port = getOrCreateReverseProxyFor(node).getPort();
        getOrCreateLoadBalancerFor(application).addOrUpdateEndpoint(new Endpoint(node.host(), port));
        apply();
    }

    public ReverseProxy getOrCreateReverseProxyFor(ClusterNode node) {
        getOrCreateServer(node.host(), node.port(), node.host(), "");
        return new ReverseProxy(getOrCreateUpstream(node.host()));
    }

    @RequiredArgsConstructor
    public class ReverseProxy {
        private final NginxUpstream upstream;

        public String name() { return upstream.getName(); }

        public Integer listen() { return serverFor(upstream).map(NginxServer::getListen).orElse(null); }

        public int getPort() {
            List<HostPort> hostPorts = upstream.getHostPorts();
            if (hostPorts.isEmpty())
                return -1;
            if (hostPorts.size() > 1)
                throw new IllegalStateException("expected exactly one endpoint in reverse proxy " + upstream.getName() + " but got " + hostPorts);
            return hostPorts.get(0).getPort();
        }

        public void setPort(int port) {
            log.info("Add port of ReverseProxy " + upstream.getName() + " to " + port);
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


    public Stream<LoadBalancer> loadBalancers() {
        return nginxConfig.servers()
            .filter(this::hasLoadBalancerUpstream)
            .map(LoadBalancer::new);
    }

    private Boolean hasLoadBalancerUpstream(NginxServer server) {
        String path = trimSlashes(rootLocationProxyPass(server).getPath());
        if (path.isEmpty())
            return false;
        String upstreamName = path + "-lb";
        return upstreamFor(server).filter(upstream -> upstream.getName().equals(upstreamName)).isPresent();
    }

    private static String trimSlashes(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        if (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    public void removeFromLoadBalancer(String application, ClusterNode node) {
        if (hasLoadBalancerFor(application)) {
            log.info("remove " + node.host() + " from lb for " + application);
            getOrCreateLoadBalancerFor(application).removeHost(node.host());
            apply();
        } else {
            log.fine("no lb found for " + application);
        }
    }

    private boolean hasLoadBalancerFor(String application) {
        return nginxConfig.upstream(application + "-lb").isPresent();
    }

    private LoadBalancer getOrCreateLoadBalancerFor(String application) {
        return new LoadBalancer(application, 80, application + "-lb", application);
    }

    private Optional<NginxServer> serverFor(NginxUpstream upstream) {
        return nginxConfig.servers().filter(server -> upstreamFor(server).map(u -> u == upstream).orElse(false)).findAny();
    }

    private Optional<NginxUpstream> upstreamFor(NginxServer server) {
        return nginxConfig.upstream(rootLocationProxyPass(server).getHost());
    }

    private URI rootLocationProxyPass(NginxServer server) {
        return server.location("/").orElseThrow(IllegalStateException::new).getProxyPass();
    }

    @NoArgsConstructor(force = true)
    public class LoadBalancer {
        private final NginxServer server;
        private final NginxUpstream upstream;

        LoadBalancer(NginxServer server) {
            this.server = server;
            this.upstream = upstreamFor(server).orElseThrow(IllegalStateException::new);
        }

        LoadBalancer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
            this.server = getOrCreateServer(fromPattern, fromListen, upstreamName, upstreamPath);
            this.upstream = getOrCreateUpstream(upstreamName);
        }

        public String name() { return upstream.getName(); }

        public String method() { return upstream.getMethod(); }

        public void updatePort(Endpoint endpoint, Integer newPort) {
            log.info("LB port doesn't match actual: " + endpoint + " -> " + newPort);
            upstream.setPort(toHostPort(endpoint), newPort);
        }

        public boolean hasHost(String host) { return upstream.hasHost(host); }

        public void removeHost(String host) {
            upstream.removeHost(host);
            if (upstream.isEmpty())
                remove();
        }

        private void remove() {
            nginxConfig.removeServer(server);
            nginxConfig.removeUpstream(upstream);
        }

        public boolean hasEndpoint(Endpoint endpoint) { return getEndpoints().contains(endpoint); }

        public List<Endpoint> getEndpoints() {
            return upstream.hostPorts().map(Tools::toEndpoint).collect(toList());
        }

        public void addOrUpdateEndpoint(Endpoint endpoint) {
            if (upstream.hasHost(endpoint.getHost())) {
                log.info("Update endpoint " + endpoint + " to LB " + upstream.getName());
                upstream.updateHostPort(toHostPort(endpoint));
            } else {
                log.info("Add missing endpoint " + endpoint + " to LB " + upstream.getName());
                upstream.addHostPort(toHostPort(endpoint));
            }
        }
    }

    private NginxServer getOrCreateServer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
        NginxServer server = nginxConfig.server(fromPattern, fromListen).orElseGet(() -> {
            log.info("Create missing LB server: " + fromPattern);
            NginxServer newServer = NginxServer.named(fromPattern).setListen(fromListen);
            nginxConfig.addServer(newServer);
            return newServer;
        });
        server.location("/").orElseGet(() -> {
            log.info("Create missing LB location '/' in server: " + upstreamName);
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
            log.info("Create missing LB upstream: " + upstreamName);
            NginxUpstream newUpstream = NginxUpstream
                .named(upstreamName)
                .setMethod("least_conn");
            nginxConfig.addUpstream(newUpstream);
            return newUpstream;
        });
    }
}
