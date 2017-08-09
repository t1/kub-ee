package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.*;
import com.github.t1.nginx.NginxConfig.*;
import lombok.AllArgsConstructor;

import java.util.Optional;
import java.util.function.*;
import java.util.stream.Stream;

@AllArgsConstructor
abstract class LoadBalancerAction {
    private NginxConfig config;
    private String deployableName;
    private Stage stage;
    private Consumer<NginxConfig> then;


    String deployableName() { return deployableName; }

    String loadBalancerName() { return serverName() + "-lb"; }

    String serverName() { return stage.getPrefix() + deployableName + stage.getSuffix(); }


    Stream<NginxServer> servers() { return config.servers(); }

    Optional<NginxServer> server(String name) { return config.server(name); }

    Optional<NginxServer> server(HostPort hostPort) { return config.server(hostPort); }

    Optional<NginxUpstream> upstream(String name) { return config.upstream(name); }


    void config(Function<NginxConfig, NginxConfig> function) { config = function.apply(config); }

    void done() { this.then.accept(config); }
}
