package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.HostPort;
import com.github.t1.nginx.NginxConfig;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Optional;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.http.ProblemDetail.badRequest;
import static com.github.t1.kubee.tools.http.ProblemDetail.internalServerError;
import static com.github.t1.nginx.NginxConfig.NginxServer;
import static com.github.t1.nginx.NginxConfig.NginxServerLocation;
import static com.github.t1.nginx.NginxConfig.NginxUpstream;

@Slf4j
public class LoadBalancerAddAction extends LoadBalancerAction {
    LoadBalancerAddAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void addTarget(URI uri) { addTarget(HostPort.of(uri)); }

    private void addTarget(HostPort hostPort) {
        log.debug("add {} to lb {}", hostPort, loadBalancerName());
        NginxUpstream upstream = removeOrCreateUpstream();

        induceProxyLocation();

        NginxUpstream with = withUpstreamServer(upstream, hostPort);
        config.addUpstream(with);
        done();
    }

    private NginxUpstream removeOrCreateUpstream() {
        Optional<NginxUpstream> upstream = upstream(loadBalancerName());
        if (!upstream.isPresent())
            return createUpstream(loadBalancerName());
        config.removeUpstream(loadBalancerName());
        return upstream.get();
    }

    private NginxUpstream createUpstream(String name) {
        log.debug("create upstream {}", name);
        return NginxUpstream.named(name).setMethod("least_conn");
    }

    private void induceProxyLocation() {
        if (!server(serverName()).isPresent())
            config.addServer(NginxServer.named(serverName()));
        NginxServer server = server(serverName()).orElseThrow(()
            -> internalServerError().detail("proxy '" + serverName() + "' still not created").exception());
        if (!server.location("/").isPresent())
            config.addServer(server.addLocation(createLocation()));
    }

    private NginxServerLocation createLocation() {
        URI proxyPass = URI.create("http://" + loadBalancerName() + "/" + deployableName() + "/");
        return NginxServerLocation
            .named("/")
            .setProxyPass(proxyPass)
            .setAfter("proxy_set_header Host      $host;\n"
                + "            proxy_set_header X-Real-IP $remote_addr;");
    }

    private NginxUpstream withUpstreamServer(NginxUpstream upstream, HostPort hostPort) {
        if (upstream.getHostPorts().contains(hostPort))
            throw badRequest().detail("server " + hostPort + " already in lb: " + upstream.getName()).exception();
        return upstream.addHostPort(hostPort);
    }
}
