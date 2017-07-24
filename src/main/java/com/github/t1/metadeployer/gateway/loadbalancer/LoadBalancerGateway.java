package com.github.t1.metadeployer.gateway.loadbalancer;

import com.github.t1.metadeployer.model.*;
import com.github.t1.metadeployer.model.ReverseProxy.ReverseProxyBuilder;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.BadRequestException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.List;

import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.*;

@Slf4j
public class LoadBalancerGateway {
    private static final Path NGINX_CONFIG = Paths.get("/usr/local/etc/nginx/nginx.conf");

    public List<LoadBalancer> getLoadBalancers() {
        return readNginxConfig()
                .getUpstreams()
                .stream()
                .map(server -> LoadBalancer
                        .builder()
                        .name(server.getName())
                        .method(server.getMethod())
                        .servers(server.getServers())
                        .build())
                .collect(toList());
    }

    private NginxConfig readNginxConfig() {
        return NginxConfig.readFrom(NGINX_CONFIG.toUri());
    }

    public List<ReverseProxy> getReverseProxies() {
        return readNginxConfig()
                .getServers()
                .stream()
                .map(this::buildReverseProxy)
                .collect(toList());
    }

    private ReverseProxy buildReverseProxy(NginxServer server) {
        ReverseProxyBuilder builder = ReverseProxy.builder();
        builder.from(URI.create("http://" + server.getName() + ":" + server.getListen()));
        server.getLocations().forEach(location -> builder.target(URI.create(location.getPass())));
        return builder.build();
    }

    public void removeFromLB(URI uri, String deployableName) {
        log.debug("remove {} from lb {}", deployableName, uri);
        NginxConfig config = withoutLoadBalancingTarget(uri, deployableName);
        log.debug("write config");
        writeNginxConfig(config);
        log.debug("reload nginx");
        nginxReload();
    }

    private NginxConfig withoutLoadBalancingTarget(URI uri, String deployableName) {
        NginxConfig config = readNginxConfig();
        NginxUpstream with = getLoadBalancer(config, deployableName);
        URI serverUri = getProxyServerUri(uri, config);
        NginxUpstream without = removeLoadBalancerServer(with, serverUri);
        return config.withoutUpstream(with.getName()).withUpstream(without);
    }

    private NginxUpstream removeLoadBalancerServer(NginxUpstream upstream, URI serverUri) {
        String server = serverUri.getHost() + ":" + serverUri.getPort();
        if (!upstream.getServers().contains(server))
            throw new BadRequestException("server " + server + " not in lb: " + upstream.getName());
        return upstream.withoutServer(server);
    }

    private NginxUpstream getLoadBalancer(NginxConfig config, String deployableName) {
        String loadBalancerName = deployableName + "-lb"; // TODO find uri in servers instead of lb naming convention
        return config
                .upstreams()
                .filter(upstream -> upstream.getName().equals(loadBalancerName))
                .findAny()
                .orElseThrow(() -> new BadRequestException("LB not found for application '" + deployableName + "'"));
    }

    private URI getProxyServerUri(URI uri, NginxConfig config) {
        List<NginxServerLocation> locations = config
                .servers()
                .filter(s -> s.getName().equals(uri.getHost()))
                .filter(s -> s.getListen().equals(Integer.toString(uri.getPort())))
                .findAny()
                .orElseThrow(() -> new BadRequestException("No server found for uri " + uri))
                .getLocations();
        if (locations.size() != 1)
            throw new BadRequestException(
                    "Expected exactly one location on server " + uri + " but found " + locations);
        return URI.create(locations.get(0).getPass());
    }

    @SneakyThrows(IOException.class)
    private void writeNginxConfig(NginxConfig config) {
        Files.write(NGINX_CONFIG, config.toString().getBytes());
    }

    @SneakyThrows({ IOException.class, InterruptedException.class })
    private void nginxReload() {
        ProcessBuilder builder = new ProcessBuilder("/usr/local/bin/nginx", "-s", "reload");
        Process process = builder.start();
        boolean inTime = process.waitFor(10, SECONDS);
        if (!inTime)
            throw new BadRequestException("could not reload nginx in time");
        if (process.exitValue() != 0)
            throw new BadRequestException("nginx reload with error");
    }

    public void addToLB(URI uri, String deployableName) {
        log.debug("add {} to lb {}", deployableName, uri);
        NginxConfig config = withLoadBalancingTarget(uri, deployableName);
        log.debug("write config");
        writeNginxConfig(config);
        log.debug("reload nginx");
        nginxReload();
    }

    private NginxConfig withLoadBalancingTarget(URI uri, String deployableName) {
        NginxConfig config = readNginxConfig();
        NginxUpstream without = getLoadBalancer(config, deployableName);
        URI serverUri = getProxyServerUri(uri, config);
        NginxUpstream with = addLoadBalancerServer(without, serverUri);
        return config.withoutUpstream(without.getName()).withUpstream(with);
    }

    private NginxUpstream addLoadBalancerServer(NginxUpstream upstream, URI serverUri) {
        String server = serverUri.getHost() + ":" + serverUri.getPort();
        if (upstream.getServers().contains(server))
            throw new BadRequestException("server " + server + " already in lb: " + upstream.getName());
        return upstream.withServer(server);
    }
}
