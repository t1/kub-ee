package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.NginxConfig;
import com.github.t1.nginx.NginxConfig.NginxUpstream;
import lombok.AllArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.function.Consumer;

@Slf4j
@Setter @Accessors(fluent = true, chain = true)
@AllArgsConstructor
abstract class LoadBalancerAction {
    protected NginxConfig config;
    protected String deployableName;
    private Stage stage;
    private Consumer<NginxConfig> then;


    String loadBalancerName() { return serverName() + "-lb"; }

    String serverName() { return stage.getPrefix() + deployableName + stage.getSuffix(); }


    Optional<NginxUpstream> upstream(String name) { return config.upstream(name); }


    void done() { this.then.accept(config); }
}
