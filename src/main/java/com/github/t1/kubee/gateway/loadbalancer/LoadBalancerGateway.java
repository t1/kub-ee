package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.ReverseProxy.*;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

import static com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway.ReloadMode.*;
import static java.lang.ProcessBuilder.Redirect.*;
import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.*;

/**
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Slf4j
public class LoadBalancerGateway {
    private static final Path NGINX_CONFIG = Paths.get("/usr/local/etc/nginx/nginx.conf");

    // TODO make this (and the port) configurable
    private ReloadMode reloadMode = service;

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
        server.getLocations().forEach(location -> builder.location(toLocation(location)));
        return builder.build();
    }

    private Location toLocation(NginxServerLocation location) {
        return Location.builder()
                       .fromPath((location.getName()))
                       .target(URI.create(location.getPass()))
                       .build();
    }

    public void removeFromLB(URI uri, String deployableName, Stage stage) {
        log.debug("remove {} from lb {}", deployableName, uri);
        Optional<NginxConfig> optional = withoutLoadBalancingTarget(uri, deployableName, stage);
        if (optional.isPresent()) {
            log.debug("write config");
            writeNginxConfig(optional.get());
            log.debug("reload nginx");
            nginxReload();
        } else {
            log.debug("{} is not in lb {}", deployableName, uri);
        }
    }

    private Optional<NginxConfig> withoutLoadBalancingTarget(URI uri, String deployableName, Stage stage) {
        NginxConfig config = readNginxConfig();
        NginxUpstream with = getLoadBalancer(config, deployableName, stage);
        URI serverUri = getProxyServerUri(uri, config);
        Optional<NginxUpstream> optional = removeLoadBalancerServer(with, serverUri);
        return optional.map(without -> config.withoutUpstream(with.getName()).withUpstream(without));
    }

    private Optional<NginxUpstream> removeLoadBalancerServer(NginxUpstream upstream, URI serverUri) {
        String server = serverUri.getHost() + ":" + serverUri.getPort();
        if (!upstream.getServers().contains(server))
            return Optional.empty();
        NginxUpstream without = upstream.withoutServer(server);
        if (without.getServers().isEmpty())
            throw new BadRequestException("can't remove last server from lb");
        return Optional.of(without);
    }

    private NginxUpstream getLoadBalancer(NginxConfig config, String deployableName, Stage stage) {
        String loadBalancerName = stage.getPrefix() + deployableName + stage.getSuffix() + "-lb";
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

    enum ReloadMode implements Callable<String> {
        service {
            @Override public String call() { return new NginxReloadService.Adapter().call(); }
        },

        setUserIdScript {
            @Override public String call() { return run("nginx-reload"); }
        },

        direct {
            @Override public String call() { return run("/usr/local/bin/nginx", "-s", "reload"); }
        };

        private static String run(String... command) {
            try {
                ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(INHERIT);
                Process process = builder.start();
                boolean inTime = process.waitFor(10, SECONDS);
                if (!inTime)
                    return "could not reload nginx in time";
                if (process.exitValue() != 0)
                    return "nginx reload with error";
                return null;
            } catch (InterruptedException | IOException e) {
                log.warn("reload failed", e);
                return "reload failed: " + e.getMessage();
            }
        }
    }

    @SneakyThrows(Exception.class) private void nginxReload() {
        String result = reloadMode.call();
        if (result != null)
            throw new FailedToReloadLoadBalancerException(result);
    }


    private class FailedToReloadLoadBalancerException extends ClientErrorException {
        private FailedToReloadLoadBalancerException(String message) { super(message, CONFLICT); }
    }

    public void addToLB(URI uri, String deployableName, Stage stage) {
        log.debug("add {} to lb {}", deployableName, uri);
        NginxConfig config = withLoadBalancingTarget(uri, deployableName, stage);
        log.debug("write config");
        writeNginxConfig(config);
        log.debug("reload nginx");
        nginxReload();
    }

    private NginxConfig withLoadBalancingTarget(URI uri, String deployableName, Stage stage) {
        NginxConfig config = readNginxConfig();
        NginxUpstream without = getLoadBalancer(config, deployableName, stage);
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
