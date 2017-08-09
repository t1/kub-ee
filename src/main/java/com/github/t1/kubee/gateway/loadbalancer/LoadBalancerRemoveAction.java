package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.*;
import com.github.t1.nginx.NginxConfig.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static java.util.stream.Collectors.*;

@Slf4j
public class LoadBalancerRemoveAction extends LoadBalancerAction {
    LoadBalancerRemoveAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void removeTarget(URI target) { removeTarget(HostPort.of(target)); }

    private void removeTarget(HostPort hostPort) {
        log.debug("remove {} from lb {}", hostPort, loadBalancerName());
        NginxServer server = server(hostPort)
                .orElseThrow(() -> badRequest().detail("no proxy found for " + hostPort).exception());
        HostPort location = HostPort.of(server
                .location("/")
                .orElseThrow(() -> badRequest().detail("proxy " + hostPort + " has no location '/'").exception())
                .getProxyPass());
        log.debug("found server {} for {}", location, hostPort);
        Optional<NginxUpstream> upstreamOptional = upstream(loadBalancerName());
        if (upstreamOptional.isPresent()) {
            NginxUpstream upstream = upstreamOptional.get();
            if (upstream.getServers().contains(location)) {
                removeLocation(loadBalancerName(), upstream, location);
            } else {
                log.debug("server {} not in upstream {}", location, loadBalancerName());
            }
        } else {
            log.debug("no upstream '{}' found", loadBalancerName());
        }
    }

    private void removeLocation(String name, NginxUpstream upstream, HostPort hostPort) {
        config(config -> config.withoutUpstream(name));
        config(config -> (upstream.getServers().size() > 1)
                ? config.withUpstream(upstream.withoutServer(hostPort))
                : config.withServers(config.servers()
                                           .map(server -> server.withoutLocation(upstream))
                                           .collect(toList())));
        done();
    }
}
