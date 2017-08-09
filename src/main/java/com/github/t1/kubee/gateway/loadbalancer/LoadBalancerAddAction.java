package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.nginx.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static com.github.t1.nginx.NginxConfig.*;

@Slf4j
@AllArgsConstructor
public class LoadBalancerAddAction {
    private NginxConfig config;
    private String loadBalancerName;
    private Consumer<NginxConfig> then;

    public void addTarget(URI uri) { addTarget(HostPort.of(uri)); }

    private void addTarget(HostPort hostPort) {
        log.debug("add {} to lb {}", hostPort, loadBalancerName);
        Optional<NginxUpstream> without = config.upstream(loadBalancerName);
        if (without.isPresent())
            config = config.withoutUpstream(loadBalancerName);
        else
            without = Optional.of(createLoadBalancer(loadBalancerName));
        URI serverUri = getProxyServerUri(hostPort);
        NginxUpstream with = addLoadBalancerServer(without.get(), serverUri);
        then.accept(config.withUpstream(with));
    }

    private NginxUpstream createLoadBalancer(String name) {
        return NginxUpstream.builder().name(name).method("least_conn").build();
    }

    private URI getProxyServerUri(HostPort hostPort) {
        List<NginxServerLocation> locations = config
                .servers()
                .filter(s -> s.getName().equals(hostPort.getHost()))
                .filter(s -> s.getListen() == hostPort.getPort())
                .findAny()
                .orElseThrow(() -> badRequest().detail("No server found for " + hostPort).exception())
                .getLocations();
        if (locations.size() != 1)
            throw badRequest()
                    .detail("Expected exactly one location on server " + hostPort + " but found " + locations)
                    .exception();
        return locations.get(0).getProxyPass();
    }

    private NginxUpstream addLoadBalancerServer(NginxUpstream upstream, URI serverUri) {
        return addLoadBalancerServer(upstream, HostPort.of(serverUri));
    }

    private NginxUpstream addLoadBalancerServer(NginxUpstream upstream, HostPort hostPort) {
        if (upstream.getServers().contains(hostPort))
            throw badRequest().detail("server " + hostPort + " already in lb: " + upstream.getName()).exception();
        return upstream.withServer(hostPort);
    }
}
