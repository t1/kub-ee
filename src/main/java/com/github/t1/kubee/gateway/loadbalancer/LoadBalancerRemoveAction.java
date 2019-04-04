package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

import static com.github.t1.kubee.tools.Tools.toHostPort;

@Slf4j
public class LoadBalancerRemoveAction extends LoadBalancerAction {
    LoadBalancerRemoveAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void removeTarget(ClusterNode target) {
        HostPort hostPort = toHostPort(target.endpoint());
        log.debug("remove {} from lb {}", hostPort, loadBalancerName());
        upstream(loadBalancerName()).map(upstream -> {
            if (upstream.getHostPorts().contains(hostPort)) {
                if (upstream.getHostPorts().size() > 1) {
                    upstream.removeHostPort(hostPort);
                } else {
                    config.removeServer(hostPort);
                }
                done();
            } else {
                log.debug("server {} not in upstream {}", hostPort, loadBalancerName());
            }
            return null;
        }).orElseGet(() -> {
            log.debug("no upstream '{}' found", loadBalancerName());
            return null;
        });
    }
}
