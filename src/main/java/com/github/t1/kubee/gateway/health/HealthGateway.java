package com.github.t1.kubee.gateway.health;

import com.github.t1.kubee.model.Cluster.HealthConfig;
import com.github.t1.kubee.model.ClusterNode;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.net.URI;

import static javax.ws.rs.core.Response.Status.*;

@Slf4j
public class HealthGateway {
    private final Client httpClient = ClientBuilder.newClient();

    public boolean fetch(ClusterNode node, String name) {
        HealthConfig healthConfig = node.getCluster().getHealthConfig();
        if (healthConfig == null) {
            log.debug("no health config for {}", node);
            return true;
        }

        URI uri = UriBuilder.fromUri(node.uri()).path(name).path(healthConfig.getPath()).build();
        log.debug("get check from {}", uri);
        Response response = httpClient.target(uri).request().get();
        log.debug("got check response: {} {}:\n{}", response.getStatus(),
                response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class));
        return response.getStatus() == OK.getStatusCode();
    }
}
