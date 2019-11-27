package com.github.t1.kubee.boundary.gateway.health;

import com.github.t1.kubee.entity.Cluster.HealthConfig;
import com.github.t1.kubee.entity.ClusterNode;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static javax.ws.rs.core.Response.Status.OK;

@Slf4j
public class HealthGateway {
    private final Client httpClient = ClientBuilder.newClient();

    public boolean fetch(ClusterNode node, String path) {
        HealthConfig healthConfig = node.getCluster().getHealthConfig();
        if (healthConfig == null || healthConfig.getPath() == null) {
            log.debug("no health config for {}", node);
            return true;
        }

        URI uri = UriBuilder.fromUri(node.uri()).path(path).path(healthConfig.getPath()).build();
        log.debug("get check from {}", uri);
        Response response = httpClient.target(uri).request().get();
        log.debug("got check response: {} {}:\n{}", response.getStatus(),
            response.getStatusInfo().getReasonPhrase(), response.readEntity(String.class));
        return response.getStatus() == OK.getStatusCode();
    }
}
