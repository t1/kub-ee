package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Endpoint;
import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.NginxConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

@Slf4j
public class LoadBalancerAddAction extends LoadBalancerAction {
    private final IngressConfig ingressConfig;

    LoadBalancerAddAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
        this.ingressConfig = new IngressConfig(config, log::info);
    }

    public void addTarget(ClusterNode node) {
        if (!ingressConfig.hasReverseProxyFor(node))
            throw new IllegalStateException("no reverse proxy found for " + node);
        int port = ingressConfig.getOrCreateReverseProxyFor(node).getPort();
        ingressConfig.getOrCreateLoadBalancerFor(node.getCluster(), deployableName)
            .addOrUpdateEndpoint(new Endpoint(node.host(), port));
        done();
    }
}
