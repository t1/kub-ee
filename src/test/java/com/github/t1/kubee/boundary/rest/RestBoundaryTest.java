package com.github.t1.kubee.boundary.rest;

import com.github.t1.kubee.TestData;
import com.github.t1.kubee.boundary.rest.RestBoundary.DeployableNotFoundException;
import com.github.t1.kubee.boundary.rest.RestBoundary.GetDeploymentResponse;
import com.github.t1.kubee.control.ClusterReconditioner;
import com.github.t1.kubee.control.Controller;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.DeploymentId;
import com.github.t1.kubee.entity.LoadBalancer;
import com.github.t1.kubee.entity.ReverseProxy;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.tools.http.ProblemDetail;
import com.github.t1.kubee.tools.http.WebApplicationApplicationException;
import org.glassfish.jersey.uri.internal.JerseyUriBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.DEPLOYMENT;
import static com.github.t1.kubee.TestData.NODE1;
import static com.github.t1.kubee.TestData.STAGE;
import static com.github.t1.kubee.TestData.VERSIONS_STATUS;
import static com.github.t1.kubee.TestData.VERSION_101;
import static com.github.t1.kubee.boundary.rest.RestBoundary.DeploymentMode.balance;
import static com.github.t1.kubee.boundary.rest.RestBoundary.DeploymentMode.deploy;
import static com.github.t1.kubee.boundary.rest.RestBoundary.DeploymentMode.unbalance;
import static com.github.t1.kubee.boundary.rest.RestBoundary.DeploymentMode.undeploy;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestBoundaryTest {
    private static final LoadBalancer LOAD_BALANCER = LoadBalancer.builder().name("foo").method("bar").server("baz").build();
    private static final ReverseProxy REVERSE_PROXY = ReverseProxy.builder().from(URI.create("/foo-bar")).to(123).build();

    @Mock UriInfo uriInfo;
    @Mock Controller controller;
    @Mock ClusterReconditioner reconditioner;

    @InjectMocks RestBoundary boundary;

    @Test void shouldGetLinks() {
        given(uriInfo.getBaseUriBuilder()).will(i -> new JerseyUriBuilder());

        List<Link> links = boundary.getLinks();

        assertThat(links).extracting(Link::toString).containsExactly(
            "</load-balancers>; rel=\"load-balancers\"; title=\"Load Balancers\"",
            "</reverse-proxies>; rel=\"reverse-proxies\"; title=\"Reverse Proxies\"",
            "</clusters>; rel=\"clusters\"; title=\"Clusters\"",
            "</slots>; rel=\"slots\"; title=\"Slots\"",
            "</stages>; rel=\"stages\"; title=\"Stages\"",
            "</deployments>; rel=\"deployments\"; title=\"Deployments\""
        );
    }

    @Test void shouldGetLoadBalancers() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));
        given(controller.loadBalancers(any())).will(i -> {
            Stream<Stage> stages = i.getArgument(0);
            assertThat(stages).containsExactly(STAGE);
            return Stream.of(LOAD_BALANCER);
        });

        List<LoadBalancer> loadBalancers = boundary.getLoadBalancers();

        assertThat(loadBalancers).containsExactly(LOAD_BALANCER);
    }

    @Test void shouldGetReverseProxies() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));
        given(controller.reverseProxies(any())).will(i -> {
            Stream<Stage> stages = i.getArgument(0);
            assertThat(stages).containsExactly(STAGE);
            return Stream.of(REVERSE_PROXY);
        });

        List<ReverseProxy> proxies = boundary.getReverseProxies();

        assertThat(proxies).containsExactly(REVERSE_PROXY);
    }

    @Test void shouldGetClusters() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        List<Cluster> clusters = boundary.getClusters();

        assertThat(clusters).containsExactly(CLUSTER);
    }

    @Test void shouldGetKnownCluster() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Cluster clusters = boundary.getCluster(CLUSTER.getSimpleName());

        assertThat(clusters).isEqualTo(CLUSTER);
    }

    @Test void shouldNotGetUnknownCluster() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Throwable throwable = catchThrowable(() -> boundary.getCluster("unknown"));

        assertThat(throwable).isInstanceOf(NotFoundException.class)
            .hasMessage("cluster not found: 'unknown'");
    }

    @Test void shouldGetSlots() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        List<Slot> slots = boundary.getSlots();

        assertThat(slots).containsExactly(TestData.SLOT_0);
    }

    @Test void shouldGetKnownSlot() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Slot slot = boundary.getSlot(TestData.SLOT_0.getName());

        assertThat(slot).isEqualTo(TestData.SLOT_0);
    }

    @Test void shouldGetUnknownSlot() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Throwable throwable = catchThrowable(() -> boundary.getSlot("unknown"));

        assertThat(throwable).isInstanceOf(NotFoundException.class)
            .hasMessage("slot not found: 'unknown'");
    }

    @Test void shouldGetStages() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        List<Stage> stages = boundary.getStages();

        assertThat(stages).containsExactly(STAGE);
    }

    @Test void shouldGetKnownStage() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Stage stage = boundary.getStage(STAGE.getName());

        assertThat(stage).isEqualTo(STAGE);
    }

    @Test void shouldGetUnknownStage() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Throwable throwable = catchThrowable(() -> boundary.getStage("unknown"));

        assertThat(throwable).isInstanceOf(NotFoundException.class)
            .hasMessage("stage not found: 'unknown'");
    }

    @Test void shouldGetDeployments() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));
        given(controller.fetchDeploymentsOn(NODE1)).willReturn(Stream.of(DEPLOYMENT));

        List<Deployment> deployments = boundary.getDeployments();

        assertThat(deployments).containsExactly(DEPLOYMENT);
    }

    @Test void shouldGetKnownDeployment() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));
        given(controller.fetchDeploymentsOn(NODE1)).willReturn(Stream.of(DEPLOYMENT));
        given(controller.fetchVersions(NODE1, DEPLOYMENT)).willReturn(VERSIONS_STATUS);

        GetDeploymentResponse deployments = boundary.getDeployment(DEPLOYMENT.id());

        assertThat(deployments).isEqualTo(new GetDeploymentResponse(DEPLOYMENT.id(), VERSIONS_STATUS));
    }

    @Test void shouldGetUnknownDeployment() {
        given(controller.clusters()).willReturn(Stream.of(CLUSTER));

        Throwable throwable = catchThrowable(() -> boundary.getDeployment(new DeploymentId(NODE1.id() + ":unknown")));

        assertThat(throwable).isInstanceOf(DeployableNotFoundException.class)
            .hasMessage("deployable 'unknown' not found on '" + NODE1 + "'");
    }

    @Test void shouldFailToPostDeploymentWithoutId() {
        Throwable throwable = catchThrowable(() -> boundary.postDeployments(null, VERSION_101, deploy));

        ProblemDetail detail = ((WebApplicationApplicationException) throwable).getDetail();
        assertThat(detail.getStatus()).isEqualTo(BAD_REQUEST);
        assertThat(detail.getDetail()).isEqualTo("id is a required parameter");
    }

    @Test void shouldFailToPostDeploymentWithoutMode() {
        Throwable throwable = catchThrowable(() -> boundary.postDeployments(DEPLOYMENT.id(), VERSION_101, null));

        ProblemDetail detail = ((WebApplicationApplicationException) throwable).getDetail();
        assertThat(detail.getStatus()).isEqualTo(BAD_REQUEST);
        assertThat(detail.getDetail()).isEqualTo("mode is a required parameter");
    }

    @Test void shouldFailToPostDeploymentWithoutVersion() {
        Throwable throwable = catchThrowable(() -> boundary.postDeployments(DEPLOYMENT.id(), null, deploy));

        ProblemDetail detail = ((WebApplicationApplicationException) throwable).getDetail();
        assertThat(detail.getStatus()).isEqualTo(BAD_REQUEST);
        assertThat(detail.getDetail()).isEqualTo("version is a required parameter when deploying");
    }

    @Test void shouldPostDeploymentDeploy() {
        boundary.postDeployments(DEPLOYMENT.id(), VERSION_101, deploy);

        verify(controller).deploy(DEPLOYMENT.id(), VERSION_101);
    }

    @Test void shouldPostDeploymentBalance() {
        boundary.postDeployments(DEPLOYMENT.id(), VERSION_101, balance);

        verify(controller).balance(DEPLOYMENT.id());
    }

    @Test void shouldPostDeploymentUnbalance() {
        boundary.postDeployments(DEPLOYMENT.id(), VERSION_101, unbalance);

        verify(controller).unbalance(DEPLOYMENT.id());
    }

    @Test void shouldPostDeploymentUndeploy() {
        boundary.postDeployments(DEPLOYMENT.id(), VERSION_101, undeploy);

        verify(controller).undeploy(DEPLOYMENT.id());
    }

    @Test void shouldPostRecondition() {
        boundary.postRecondition();

        verify(reconditioner).run();
    }
}
