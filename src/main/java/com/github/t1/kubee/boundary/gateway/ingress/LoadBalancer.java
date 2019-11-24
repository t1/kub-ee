package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.entity.Endpoint;

import java.util.stream.Stream;

public interface LoadBalancer {
    String applicationName();

    String method();

    void updatePort(Endpoint endpoint, Integer newPort);

    boolean hasHost(String host);

    int indexOf(String host);

    void removeHost(String host);

    boolean hasEndpoint(Endpoint endpoint);

    /** Returns a snapshot of the endpoints, so it can be manipulated (ConcurrentModificationException) */
    Stream<Endpoint> endpoints();

    void addOrUpdateEndpoint(Endpoint endpoint);
}
