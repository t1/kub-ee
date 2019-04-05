package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.LoadBalancer;
import com.github.t1.kubee.model.ReverseProxy;
import com.github.t1.kubee.model.ReverseProxy.Location;
import com.github.t1.kubee.model.ReverseProxy.ReverseProxyBuilder;
import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxServer;
import com.github.t1.nginx.NginxConfig.NginxServerLocation;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

/**
 * Controls the load balancers and reverse proxies. The public interface is generic, the implementation is NGINX specific.
 * see https://www.nginx.com/resources/admin-guide/load-balancer/
 */
@Slf4j
public class IngressGateway {
    Map<String, IngressConfigAdapter> configAdapters = new LinkedHashMap<>();

    public IngressConfigAdapter config(Stage stage) {
        return configAdapters.computeIfAbsent(stage.getName(), name -> new IngressConfigAdapter(stage));
    }

    private NginxConfig read(Stage stage) { return config(stage).read(); }

    public Stream<LoadBalancer> loadBalancers(Stage stage) {
        return read(stage).upstreams().map(this::buildLoadBalancer);
    }

    private LoadBalancer buildLoadBalancer(NginxUpstream server) {
        return LoadBalancer
            .builder()
            .name(server.getName())
            .method(server.getMethod())
            .servers(server.hostPorts().map(HostPort::toString).collect(toList()))
            .build();
    }


    public Stream<ReverseProxy> reverseProxies(Stage stage) {
        return read(stage).getServers().stream().map(this::buildReverseProxy);
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


    public void remove(String deployableName, ClusterNode node) {
        IngressConfigAdapter adapter = config(node.getStage());
        IngressConfig ingressConfig = new IngressConfig(adapter.configPath, log::info);

        log.debug("remove {} from lb for {}", node.endpoint(), deployableName);
        if (!ingressConfig.hasLoadBalancerFor(deployableName)) {
            log.debug("no lb found for {}", deployableName);
            return;
        }
        ingressConfig.getOrCreateLoadBalancerFor(deployableName)
            .removeHost(node.host());
        ingressConfig.apply();
        adapter.update(ingressConfig.nginxConfig);
    }

    public void add(String deployableName, ClusterNode node) {
        IngressConfigAdapter adapter = config(node.getStage());
        IngressConfig ingressConfig = new IngressConfig(adapter.configPath, log::info);

        if (!ingressConfig.hasReverseProxyFor(node))
            throw new IllegalStateException("no reverse proxy found for " + node);
        int port = ingressConfig.getOrCreateReverseProxyFor(node).getPort();
        ingressConfig.getOrCreateLoadBalancerFor(deployableName)
            .addOrUpdateEndpoint(new Endpoint(node.host(), port));
        ingressConfig.apply();
        adapter.update(ingressConfig.nginxConfig);
    }
}
