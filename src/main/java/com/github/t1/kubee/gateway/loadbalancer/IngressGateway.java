package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.LoadBalancer;
import com.github.t1.kubee.model.ReverseProxy;
import com.github.t1.kubee.model.Stage;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static com.github.t1.kubee.tools.http.ProblemDetail.internalServerError;
import static java.util.stream.Collectors.toList;

/**
 * Controls the load balancers and reverse proxies. The public interface is generic, the implementation is NGINX specific.
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Slf4j
public class IngressGateway {
    @VisibleForTesting
    public static Path NGINX_ETC = Paths.get("/usr/local/etc/nginx");

    private static Ingress ingressConfig(Stage stage) {
        return new Ingress(configPath(stage), log::info);
    }

    private static Path configPath(Stage stage) {
        return NGINX_ETC.resolve(stage.getLoadBalancerConfig().getOrDefault("config-path",
            stage.getPrefix() + "nginx" + stage.getSuffix() + ".conf"));
    }

    public Stream<LoadBalancer> loadBalancers(Stage stage) {
        return ingressConfig(stage).loadBalancers().map(config -> LoadBalancer.builder()
            .name(config.name())
            .method(config.method())
            .servers(config.getEndpoints().stream().map(Endpoint::toString).collect(toList()))
            .build());
    }

    public Stream<ReverseProxy> reverseProxies(Stage stage) {
        return ingressConfig(stage).reverseProxies().map(config -> ReverseProxy.builder()
            .from(URI.create("http://" + config.name() + ":" + config.listen()))
            .to(config.getPort())
            .build());
    }

    public void add(String deployableName, ClusterNode node) {
        Ingress ingress = ingressConfig(node.getStage());

        if (!ingress.hasReverseProxyFor(node))
            throw new IllegalStateException("no reverse proxy found for " + node.host() + " in " + Ingress.toString(ingress.reverseProxies()));
        int port = ingress.getOrCreateReverseProxyFor(node).getPort();
        ingress.getOrCreateLoadBalancerFor(deployableName).addOrUpdateEndpoint(new Endpoint(node.host(), port));
        ingress.write();
        reload(node.getStage());
    }

    public void remove(String deployableName, ClusterNode node) {
        Ingress ingress = ingressConfig(node.getStage());

        log.debug("remove {} from lb for {}", node.endpoint(), deployableName);
        if (!ingress.hasLoadBalancerFor(deployableName)) {
            log.debug("no lb found for {}", deployableName);
            return;
        }
        ingress.getOrCreateLoadBalancerFor(deployableName).removeHost(node.host());
        ingress.write();
        reload(node.getStage());
    }

    private void reload(Stage stage) {
        log.debug("reload nginx");
        String result = IngressReloadAdapter.reloadMode(stage).reload();
        if (result != null)
            throw internalServerError().detail("failed to reload load balancer: " + result).exception();
    }
}
