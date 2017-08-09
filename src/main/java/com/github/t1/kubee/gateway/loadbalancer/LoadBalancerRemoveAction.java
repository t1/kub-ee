package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.nginx.*;
import com.github.t1.nginx.NginxConfig.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static java.util.stream.Collectors.*;

@Slf4j
@RequiredArgsConstructor
public class LoadBalancerRemoveAction {
    private final NginxConfig config;
    private final String loadBalancerName;
    private final Consumer<NginxConfig> then;

    public void removeTarget(URI target) { removeTarget(HostPort.of(target)); }

    private void removeTarget(HostPort hostPort) {
        log.debug("remove {} from lb {}", hostPort, loadBalancerName);
        NginxServer server = config
                .server(hostPort)
                .orElseThrow(() -> badRequest().detail("no proxy found for " + hostPort).exception());
        HostPort location = HostPort.of(server
                .location("/")
                .orElseThrow(() -> badRequest().detail("proxy " + hostPort + " has no location '/'").exception())
                .getProxyPass());
        log.debug("found server {} for {}", location, hostPort);
        Optional<NginxUpstream> upstreamOptional = config.upstream(loadBalancerName);
        if (upstreamOptional.isPresent()) {
            NginxUpstream upstream = upstreamOptional.get();
            if (upstream.getServers().contains(location)) {
                removeLocation(config, loadBalancerName, upstream, location);
            } else {
                log.debug("server {} not in upstream {}", location, loadBalancerName);
            }
        } else {
            log.debug("no upstream '{}' found", loadBalancerName);
        }
    }

    private void removeLocation(NginxConfig config, String name, NginxUpstream upstream, HostPort hostPort) {
        config = config.withoutUpstream(name);
        config = (upstream.getServers().size() > 1)
                ? config.withUpstream(upstream.withoutServer(hostPort))
                : config.withServers(config.servers()
                                           .map(server -> withoutLocation(server, upstream))
                                           .collect(toList()));
        then.accept(config);
    }

    private NginxServer withoutLocation(NginxServer server, NginxUpstream upstream) {
        List<NginxServerLocation> locations = server
                .locations()
                .filter(location -> !location.passTo(upstream.getName()))
                .collect(toList());
        return server.withLocations(locations);
    }
}
