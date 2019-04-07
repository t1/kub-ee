package com.github.t1.kubee.control;

import com.github.t1.kubee.boundary.gateway.ingress.Ingress.LoadBalancer;
import com.github.t1.kubee.boundary.gateway.ingress.Ingress.ReverseProxy;
import com.github.t1.kubee.entity.Cluster;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.ClusterTest.CLUSTERS;
import static com.github.t1.kubee.entity.ClusterTest.DEV;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigsTest extends AbstractControllerTest {
    @Test void shouldGetClusters() {
        Stream<Cluster> clusters = controller.clusters();

        assertThat(clusters).containsExactly(CLUSTERS);
    }


    @Test void shouldGetNoLoadBalancers() {
        Stream<com.github.t1.kubee.entity.LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of());

        assertThat(loadBalancers).containsExactly();
    }

    @Test void shouldGetOneLoadBalancer() {
        LoadBalancer foo = mock(LoadBalancer.class);
        given(foo.name()).willReturn("foo");
        when(ingress.loadBalancers()).thenReturn(Stream.of(foo));

        Stream<com.github.t1.kubee.entity.LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(com.github.t1.kubee.entity.LoadBalancer.builder().name("foo").build());
    }

    @Test void shouldGetTwoLoadBalancer() {
        LoadBalancer foo = mock(LoadBalancer.class);
        given(foo.name()).willReturn("foo");
        LoadBalancer bar = mock(LoadBalancer.class);
        given(bar.name()).willReturn("bar");
        when(ingress.loadBalancers()).thenReturn(Stream.of(foo, bar));

        Stream<com.github.t1.kubee.entity.LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(
            com.github.t1.kubee.entity.LoadBalancer.builder().name("foo").build(),
            com.github.t1.kubee.entity.LoadBalancer.builder().name("bar").build());
    }


    @Test void shouldGetNoReverseProxies() {
        Stream<com.github.t1.kubee.entity.ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of());

        assertThat(reverseProxies).containsExactly();
    }

    @Test void shouldGetOneReverseProxy() {
        ReverseProxy foo = mock(ReverseProxy.class);
        given(foo.name()).willReturn("foo");
        given(foo.listen()).willReturn(1);
        given(foo.getPort()).willReturn(2);
        when(ingress.reverseProxies()).thenReturn(Stream.of(foo));

        Stream<com.github.t1.kubee.entity.ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(com.github.t1.kubee.entity.ReverseProxy.builder().from(URI.create("http://foo:1")).to(2).build());
    }

    @Test void shouldGetTwoReverseProxies() {
        ReverseProxy foo = mock(ReverseProxy.class);
        given(foo.name()).willReturn("foo");
        given(foo.listen()).willReturn(1);
        given(foo.getPort()).willReturn(2);

        ReverseProxy bar = mock(ReverseProxy.class);
        given(bar.name()).willReturn("bar");
        given(bar.listen()).willReturn(3);
        given(bar.getPort()).willReturn(4);
        when(ingress.reverseProxies()).thenReturn(Stream.of(foo, bar));

        Stream<com.github.t1.kubee.entity.ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(
            com.github.t1.kubee.entity.ReverseProxy.builder().from(URI.create("http://foo:1")).to(2).build(),
            com.github.t1.kubee.entity.ReverseProxy.builder().from(URI.create("http://bar:3")).to(4).build());
    }
}
