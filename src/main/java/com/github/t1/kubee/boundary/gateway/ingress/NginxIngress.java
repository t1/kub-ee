package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Endpoint;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import com.google.common.annotations.VisibleForTesting;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Log
@NoArgsConstructor(force = true)
class NginxIngress implements Ingress {
    private static final String LB_SUFFIX = "-lb";

    @VisibleForTesting
    static Path NGINX_ETC = Paths.get("/usr/local/etc/nginx");

    private static String toString(Stream<ReverseProxy> reverseProxies) {
        return reverseProxies.map(ReverseProxy::name).collect(joining(", ", "[", "]"));
    }

    private final @NotNull Stage stage;
    private final @NotNull NginxConfig nginxConfig;

    private final @NotNull Path configPath;
    private final @NotNull String original;

    NginxIngress(@NotNull Stage stage) {
        this.stage = stage;

        this.configPath = configPath();
        this.nginxConfig = NginxConfig.readFrom(configPath.toUri());
        this.original = nginxConfig.toString();
    }

    private Path configPath() {
        return NGINX_ETC.resolve(stage.getLoadBalancerConfig().getOrDefault("config-path",
            stage.getPrefix() + "nginx" + stage.getSuffix() + ".conf"));
    }

    @Override public boolean hasChanged() { return !nginxConfig.toString().equals(original); }

    @Override public void apply() {
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

    @Override public void removeReverseProxyFor(ClusterNode node) {
        nginxConfig.removeServer(new HostPort(node.host(), node.port()));
        nginxConfig.removeUpstream(node.host());
    }

    @Override public boolean hasReverseProxyFor(ClusterNode node) {
        return nginxConfig.upstream(node.host()).isPresent();
    }

    @Override public Stream<ReverseProxy> reverseProxies() {
        return nginxConfig.upstreams().filter(this::isReverseProxy).map(NginxReverseProxy::new);
    }

    private boolean isReverseProxy(NginxUpstream upstream) {
        return !upstream.getName().endsWith(LB_SUFFIX) && upstream.getHostPorts().size() == 1;
    }

    @Override public void addToLoadBalancer(String application, ClusterNode node) {
        if (!hasReverseProxyFor(node))
            throw new IllegalStateException("no reverse proxy found for " + node.host() + " in " + toString(reverseProxies()));
        int port = getOrCreateReverseProxyFor(node).getPort();
        getOrCreateLoadBalancerFor(application).addOrUpdateEndpoint(new Endpoint(node.host(), port));
        apply();
    }

    @Override public ReverseProxy getOrCreateReverseProxyFor(ClusterNode node) {
        getOrCreateServer(node.host(), node.port(), node.host(), "");
        return new NginxReverseProxy(getOrCreateUpstream(node.host()));
    }

    @RequiredArgsConstructor
    class NginxReverseProxy implements ReverseProxy {
        private final NginxUpstream upstream;

        @Override public String name() { return upstream.getName(); }

        @Override public Integer listen() { return serverFor(upstream).map(NginxServer::getListen).orElse(null); }

        @Override public int getPort() {
            List<HostPort> hostPorts = upstream.getHostPorts();
            if (hostPorts.isEmpty())
                return -1;
            if (hostPorts.size() > 1)
                throw new IllegalStateException("expected exactly one endpoint in reverse proxy " + upstream.getName() + " but got " + hostPorts);
            return hostPorts.get(0).getPort();
        }

        @Override public void setPort(int port) {
            log.info("set port of ReverseProxy " + upstream.getName() + " to " + port);
            List<HostPort> hostPorts = upstream.getHostPorts();
            log.finer("upstream was " + hostPorts);
            if (hostPorts.size() > 1)
                throw new IllegalStateException("expected no more than one endpoint in reverse proxy " + upstream.getName() + " but got " + hostPorts);
            if (hostPorts.isEmpty()) {
                hostPorts.add(new HostPort(upstream.getName(), port));
            } else {
                hostPorts.set(0, hostPorts.get(0).withPort(port));
            }
        }
    }


    @Override public Stream<LoadBalancer> loadBalancers() {
        return nginxConfig.servers()
            .filter(this::hasLoadBalancerUpstream)
            .map(server -> (LoadBalancer) new NginxLoadBalancer(server))
            .collect(toList()).stream(); // copy to protect from ConcurrentModificationException
    }

    private Boolean hasLoadBalancerUpstream(@NonNull NginxServer server) {
        String path = trimSlashes(rootLocationProxyPass(server).getPath());
        if (path.isEmpty())
            return false;
        String upstreamName = path + LB_SUFFIX;
        return upstreamFor(server).filter(upstream -> upstream.getName().equals(upstreamName)).isPresent();
    }

    private static String trimSlashes(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        if (path.startsWith("/"))
            path = path.substring(1);
        return path;
    }

    @Override public void removeFromLoadBalancer(String application, ClusterNode node) {
        if (hasLoadBalancerFor(application)) {
            log.info("remove " + node.host() + " from lb for " + application);
            getOrCreateLoadBalancerFor(application).removeHost(node.host());
            apply();
        } else {
            log.fine("no lb found for " + application);
        }
    }

    private boolean hasLoadBalancerFor(String application) {
        return nginxConfig.upstream(application + LB_SUFFIX).isPresent();
    }

    private LoadBalancer getOrCreateLoadBalancerFor(String application) {
        return new NginxLoadBalancer(application, 80, application + LB_SUFFIX, application);
    }

    private Optional<NginxServer> serverFor(NginxUpstream upstream) {
        return nginxConfig.servers().filter(server -> upstreamFor(server).map(u -> u == upstream).orElse(false)).findAny();
    }

    private Optional<NginxUpstream> upstreamFor(@NonNull NginxServer server) {
        return nginxConfig.upstream(rootLocationProxyPass(server).getHost());
    }

    private URI rootLocationProxyPass(@NonNull NginxServer server) {
        return server.location("/").orElseThrow(IllegalStateException::new).getProxyPass();
    }

    @NoArgsConstructor(force = true)
    class NginxLoadBalancer implements LoadBalancer {
        private final NginxServer server;
        private final NginxUpstream upstream;

        NginxLoadBalancer(NginxServer server) {
            this.server = server;
            this.upstream = upstreamFor(server).orElseThrow(IllegalStateException::new);
        }

        NginxLoadBalancer(String fromPattern, int fromListen, String upstreamName, String upstreamPath) {
            this.server = getOrCreateServer(fromPattern, fromListen, upstreamName, upstreamPath);
            this.upstream = getOrCreateUpstream(upstreamName);
        }

        @Override public String applicationName() {
            String upstreamName = upstream.getName();
            assert upstreamName.endsWith(LB_SUFFIX);
            return upstreamName.substring(0, upstreamName.length() - LB_SUFFIX.length());
        }

        @Override public String method() { return upstream.getMethod(); }

        @Override public void updatePort(Endpoint endpoint, Integer newPort) {
            log.info("LB port doesn't match actual: " + endpoint + " -> " + newPort);
            upstream.setPort(toHostPort(endpoint), newPort);
        }

        @Override public boolean hasHost(String host) { return upstream.hasHost(host); }

        @Override public int indexOf(String host) { return upstream.indexOf(host); }

        @Override public void removeHost(String host) {
            upstream.removeHost(host);
            if (upstream.isEmpty())
                remove();
        }

        private void remove() {
            nginxConfig.removeServer(server);
            nginxConfig.removeUpstream(upstream);
        }

        @Override public boolean hasEndpoint(Endpoint endpoint) { return endpoints().anyMatch(endpoint::equals); }

        @Override public Stream<Endpoint> endpoints() { return upstream.hostPorts().map(NginxIngress::toEndpoint).collect(toList()).stream(); }

        @Override public void addOrUpdateEndpoint(Endpoint endpoint) {
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

    private static HostPort toHostPort(Endpoint endpoint) {
        return (endpoint == null) ? null : new HostPort(endpoint.getHost(), endpoint.getPort());
    }

    static Endpoint toEndpoint(HostPort hostPort) {
        return (hostPort == null) ? null : new Endpoint(hostPort.getHost(), hostPort.getPort());
    }
}
