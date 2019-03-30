package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
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
        Optional<NginxUpstream> upstreamOptional = upstream(loadBalancerName());
        if (upstreamOptional.isPresent()) {
            NginxUpstream upstream = upstreamOptional.get();
            if (upstream.getHostPorts().contains(hostPort)) {
                removeLocation(loadBalancerName(), upstream, hostPort);
            } else {
                log.debug("server {} not in upstream {}", hostPort, loadBalancerName());
            }
        } else {
            log.debug("no upstream '{}' found", loadBalancerName());
        }
    }

    private void removeLocation(String name, NginxUpstream upstream, HostPort hostPort) {
        config(config -> config.withoutUpstream(name));
        config(config -> (upstream.getHostPorts().size() > 1)
                ? config.withUpstream(upstream.withoutHostPort(hostPort))
                : config.withoutServer(serverName()));
        done();
    }
}
