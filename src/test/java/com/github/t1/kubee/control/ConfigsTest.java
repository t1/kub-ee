package com.github.t1.kubee.control;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.LoadBalancer;
import com.github.t1.kubee.entity.ReverseProxy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.entity.ClusterTest.DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class ConfigsTest extends AbstractControllerTest {
    @Test void shouldGetClusters() {
        Stream<Cluster> clusters = controller.clusters();

        assertThat(clusters).containsExactly(CLUSTERS);
    }


    @Test void shouldGetNoLoadBalancers() {
        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of());

        assertThat(loadBalancers).containsExactly();
    }

    @Test void shouldGetOneLoadBalancer() {
        LoadBalancer foo = LoadBalancer.builder().name("foo").build();
        when(controller.ingressGateway.loadBalancers(DEV)).thenReturn(Stream.of(foo));

        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(foo);
    }

    @Test void shouldGetTwoLoadBalancer() {
        LoadBalancer foo = LoadBalancer.builder().name("foo").build();
        LoadBalancer bar = LoadBalancer.builder().name("bar").build();
        when(controller.ingressGateway.loadBalancers(DEV)).thenReturn(Stream.of(foo, bar));

        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(foo, bar);
    }


    @Test void shouldGetNoReverseProxies() {
        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of());

        assertThat(reverseProxies).containsExactly();
    }

    @Test void shouldGetOneReverseProxy() {
        ReverseProxy foo = ReverseProxy.builder().from(URI.create("foo")).build();
        when(controller.ingressGateway.reverseProxies(DEV)).thenReturn(Stream.of(foo));

        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(foo);
    }

    @Test void shouldGetTwoReverseProxies() {
        ReverseProxy foo = ReverseProxy.builder().from(URI.create("foo")).build();
        ReverseProxy bar = ReverseProxy.builder().from(URI.create("bar")).build();
        when(controller.ingressGateway.reverseProxies(DEV)).thenReturn(Stream.of(foo, bar));

        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(foo, bar);
    }
}
