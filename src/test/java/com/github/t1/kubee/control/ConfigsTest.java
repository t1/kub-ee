package com.github.t1.kubee.control;

import com.github.t1.kubee.model.*;
import org.junit.Test;

import java.net.URI;
import java.util.stream.Stream;

import static com.github.t1.kubee.model.ClusterTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ConfigsTest extends AbstractControllerTest {
    @Test
    public void shouldGetClusters() throws Exception {
        Stream<Cluster> clusters = controller.clusters();

        assertThat(clusters).containsExactly(CLUSTERS);
    }


    @Test
    public void shouldGetNoLoadBalancers() throws Exception {
        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of());

        assertThat(loadBalancers).containsExactly();
    }

    @Test
    public void shouldGetOneLoadBalancer() throws Exception {
        LoadBalancer foo = LoadBalancer.builder().name("foo").build();
        when(controller.loadBalancing.loadBalancers(DEV)).thenReturn(Stream.of(foo));

        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(foo);
    }

    @Test
    public void shouldGetTwoLoadBalancer() throws Exception {
        LoadBalancer foo = LoadBalancer.builder().name("foo").build();
        LoadBalancer bar = LoadBalancer.builder().name("bar").build();
        when(controller.loadBalancing.loadBalancers(DEV)).thenReturn(Stream.of(foo, bar));

        Stream<LoadBalancer> loadBalancers = controller.loadBalancers(Stream.of(DEV));

        assertThat(loadBalancers).containsExactly(foo, bar);
    }


    @Test
    public void shouldGetNoReverseProxies() throws Exception {
        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of());

        assertThat(reverseProxies).containsExactly();
    }

    @Test
    public void shouldGetOneReverseProxy() throws Exception {
        ReverseProxy foo = ReverseProxy.builder().from(URI.create("foo")).build();
        when(controller.loadBalancing.reverseProxies(DEV)).thenReturn(Stream.of(foo));

        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(foo);
    }

    @Test
    public void shouldGetTwoReverseProxies() throws Exception {
        ReverseProxy foo = ReverseProxy.builder().from(URI.create("foo")).build();
        ReverseProxy bar = ReverseProxy.builder().from(URI.create("bar")).build();
        when(controller.loadBalancing.reverseProxies(DEV)).thenReturn(Stream.of(foo, bar));

        Stream<ReverseProxy> reverseProxies = controller.reverseProxies(Stream.of(DEV));

        assertThat(reverseProxies).containsExactly(foo, bar);
    }
}
