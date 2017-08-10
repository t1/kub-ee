package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.*;
import com.github.t1.nginx.NginxConfig.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
public class LoadBalancerRemoveAction extends LoadBalancerAction {
    LoadBalancerRemoveAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void removeTarget(URI target) { removeTarget(HostPort.of(target)); }

    private void removeTarget(HostPort hostPort) {
        log.debug("remove {} from lb {}", hostPort, loadBalancerName());
        hostPort = resolveProxy(hostPort);
        Optional<NginxUpstream> upstreamOptional = upstream(loadBalancerName());
        if (upstreamOptional.isPresent()) {
            NginxUpstream upstream = upstreamOptional.get();
            if (upstream.getServers().contains(hostPort)) {
                removeLocation(loadBalancerName(), upstream, hostPort);
            } else {
                log.debug("server {} not in upstream {}", hostPort, loadBalancerName());
            }
        } else {
            log.debug("no upstream '{}' found", loadBalancerName());
        }
    }

    private HostPort resolveProxy(HostPort hostPort) {
        return server(hostPort)
                .flatMap(server -> server.location("/"))
                .map(location -> {
                    log.debug("found server {} for {}", location, hostPort);
                    return location;
                })
                .map(NginxServerLocation::getProxyPass)
                .map(HostPort::of)
                .orElse(hostPort);
    }

    private void removeLocation(String name, NginxUpstream upstream, HostPort hostPort) {
        config(config -> config.withoutUpstream(name));
        config(config -> (upstream.getServers().size() > 1)
                ? config.withUpstream(upstream.withoutServer(hostPort))
                : config.withoutServer(serverName()));
        done();
    }
}
