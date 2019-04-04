package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.NginxConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class LoadBalancerAddAction extends LoadBalancerAction {
    LoadBalancerAddAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void addTarget(ClusterNode node) {
        log.debug("add {} to lb {}", node.endpoint(), loadBalancerName());
        new LoadBalancerConfig(config, log::info).getOrCreateLoadBalancerFor(node);
        done();
    }
}
