package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.kubee.model.Stage;
import com.github.t1.nginx.*;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

import static com.github.t1.kubee.tools.http.ProblemDetail.*;
import static com.github.t1.nginx.NginxConfig.*;

@Slf4j
public class LoadBalancerAddAction extends LoadBalancerAction {
    LoadBalancerAddAction(NginxConfig config, String deployableName, Stage stage, Consumer<NginxConfig> then) {
        super(config, deployableName, stage, then);
    }

    public void addTarget(URI uri) { addTarget(HostPort.of(uri)); }

    private void addTarget(HostPort hostPort) {
        log.debug("add {} to lb {}", hostPort, loadBalancerName());
        NginxUpstream upstream = upstream();

        induceProxyLocation();

        URI serverUri = getProxyServerUri(hostPort);
        NginxUpstream with = addUpstreamServer(upstream, HostPort.of(serverUri));
        config(config -> config.withUpstream(with));
        done();
    }

    private NginxUpstream upstream() {
        Optional<NginxUpstream> upstream = upstream(loadBalancerName());
        if (upstream.isPresent()) {
            config(config -> config.withoutUpstream(loadBalancerName()));
            return upstream.get();
        } else {
            return createUpstream(loadBalancerName());
        }
    }

    private void induceProxyLocation() {
        if (!server(serverName()).isPresent())
            config(config -> config.withServer(NginxServer.named(serverName())));
        NginxServer server = server(serverName()).orElseThrow(()
                -> internalServerError().detail("proxy '" + serverName() + "' still not created").exception());
        if (!server.location("/").isPresent())
            config(config -> config.withoutServer(serverName()).withServer(server.withLocation(createLocation())));
    }

    private NginxServerLocation createLocation() {
        URI proxyPass = URI.create("http://" + loadBalancerName() + "/" + deployableName() + "/");
        return NginxServerLocation
                .named("/").withProxyPass(proxyPass)
                .withAfter("proxy_set_header Host      $host;\n"
                        + "            proxy_set_header X-Real-IP $remote_addr;");
    }

    private NginxUpstream createUpstream(String name) { return NginxUpstream.named(name).withMethod("least_conn"); }

    private URI getProxyServerUri(HostPort hostPort) {
        List<NginxServerLocation> locations = servers()
                .filter(s -> s.getName().equals(hostPort.getHost()))
                .filter(s -> s.getListen() == hostPort.getPort())
                .findAny()
                .orElseThrow(() -> badRequest().detail("No server found for " + hostPort).exception())
                .getLocations();
        if (locations.size() != 1)
            throw badRequest()
                    .detail("Expected exactly one location on server " + hostPort + " but found " + locations)
                    .exception();
        return locations.get(0).getProxyPass();
    }

    private NginxUpstream addUpstreamServer(NginxUpstream upstream, HostPort hostPort) {
        if (upstream.getServers().contains(hostPort))
            throw badRequest().detail("server " + hostPort + " already in lb: " + upstream.getName()).exception();
        return upstream.withServer(hostPort);
    }
}
