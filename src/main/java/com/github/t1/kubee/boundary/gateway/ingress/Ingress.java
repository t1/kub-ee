package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.kubee.entity.ClusterNode;

import java.util.stream.Stream;

/**
 * We have two use cases:
 * <ul>
 * <li>{@link LoadBalancer Load Balancer}: a set of nodes acting as a cluster for an application.</li>
 * <li>{@link ReverseProxy Reverse Proxy}: a single node in a cluster exposed to the exterior.</li>
 * </ul>
 */
public interface Ingress {
    boolean hasChanged();

    void apply();

    void removeReverseProxyFor(ClusterNode node);

    boolean hasReverseProxyFor(ClusterNode node);

    Stream<ReverseProxy> reverseProxies();

    void addToLoadBalancer(String application, ClusterNode node);

    ReverseProxy getOrCreateReverseProxyFor(ClusterNode node);

    Stream<LoadBalancer> loadBalancers();

    void removeFromLoadBalancer(String application, ClusterNode node);
}
