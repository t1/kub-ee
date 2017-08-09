package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.ReverseProxy.*;
import com.github.t1.nginx.*;
import com.github.t1.nginx.NginxConfig.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Callable;

import static com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway.ReloadMode.*;
import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static java.lang.ProcessBuilder.Redirect.*;
import static java.util.concurrent.TimeUnit.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.*;

/**
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Slf4j
public class LoadBalancerGateway {
    private static final Path DEFAULT_NGINX_CONFIG_PATH = Paths.get("/usr/local/etc/nginx/nginx.conf");

    Path nginxConfigPath = DEFAULT_NGINX_CONFIG_PATH;

    // TODO make this (and the port) configurable
    Callable<String> reloadMode = service;

    private NginxConfig readNginxConfig() { return NginxConfig.readFrom(nginxConfigPath.toUri()); }


    public List<LoadBalancer> getLoadBalancers() {
        return readNginxConfig().upstreams().map(this::buildLoadBalancer).collect(toList());
    }

    private LoadBalancer buildLoadBalancer(NginxUpstream server) {
        return LoadBalancer
                .builder()
                .name(server.getName())
                .method(server.getMethod())
                .servers(server.servers().map(HostPort::toString).collect(toList()))
                .build();
    }


    public List<ReverseProxy> getReverseProxies() {
        return readNginxConfig().getServers().stream().map(this::buildReverseProxy).collect(toList());
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
                       .target(location.getProxyPass())
                       .build();
    }

    public LoadBalancerRemoveAction from(String loadBalancerName) {
        return new LoadBalancerRemoveAction(readNginxConfig(), loadBalancerName, this::updateNginx);
    }

    public LoadBalancerAddAction to(String loadBalancerName) {
        return new LoadBalancerAddAction(readNginxConfig(), loadBalancerName, this::updateNginx);
    }

    private void updateNginx(NginxConfig config) {
        writeNginxConfig(config);
        nginxReload();
    }

    public static String loadBalancerName(String deployableName, Stage stage) {
        return serverName(deployableName, stage) + "-lb";
    }

    public static String serverName(String deployableName, Stage stage) {
        return stage.getPrefix() + deployableName + stage.getSuffix();
    }

    @SneakyThrows(IOException.class) private void writeNginxConfig(NginxConfig config) {
        log.debug("write config");
        Files.write(nginxConfigPath, config.toString().getBytes());
    }

    @SneakyThrows(Exception.class) private void nginxReload() {
        log.debug("reload nginx");
        String result = reloadMode.call();
        if (result != null)
            throw problem(CONFLICT).detail("failed to reload load balancer: " + result).exception();
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
}
