package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.ClusterTest;
import com.github.t1.kubee.model.Deployment;
import com.github.t1.kubee.model.Deployment.DeploymentBuilder;
import com.github.t1.kubee.model.Slot;
import com.github.t1.kubee.model.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.util.List;

import static com.github.t1.kubee.control.ControllerMockFactory.create;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.contentOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentListHtmlMessageBodyWriterTest {
    private DeploymentListHtmlMessageBodyWriter writer = new DeploymentListHtmlMessageBodyWriter();

    @BeforeEach void setUp() {
        writer.uriInfo = mock(UriInfo.class);
        when(writer.uriInfo.getBaseUri()).thenReturn(URI.create("http://localhost:8080/kub-ee/api/"));
    }

    private void givenClusters(List<Cluster> clusters) { writer.controller = create(clusters); }


    private DeploymentBuilder createDeployment(String name) {
        return Deployment.builder()
                         .groupId(name + "-group")
                         .artifactId(name + "-art")
                         .type(name + "-type")
                         .name(name);
    }

    private DeploymentBuilder createDeployment() { return Deployment.builder().groupId("com.github.t1").type("war"); }


    @Test void shouldWriteSimple() throws Exception {
        Stage prod = Stage.builder().name("PROD").build();
        Cluster one = Cluster.builder().host("localhost")
                             .slot(Slot.builder().name("1").http(1).build())
                             .stage(prod)
                             .build();
        Cluster two = Cluster.builder().host("localhost")
                             .slot(Slot.builder().name("2").http(2).build())
                             .stage(prod)
                             .build();
        givenClusters(asList(one, two));
        ClusterNode prod1 = prod.index(one, 1);
        ClusterNode prod2 = prod.index(two, 1);

        DeploymentBuilder deployer = createDeployment().artifactId("deployer").name("deployer").version("unknown");
        DeploymentBuilder jolokia = createDeployment().artifactId("jolokia").name("jolokia");
        List<Deployment> deployables = asList(
                deployer.node(prod1).build(),
                jolokia.version("1.3.4").node(prod1).build(),
                deployer.node(prod2).build(),
                jolokia.version("1.3.3").node(prod2).build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        URL expected = DeploymentListHtmlMessageBodyWriterTest.class.getResource("deployment-list-simple.html");
        assertThat(out.toString()).isEqualTo(contentOf(expected).trim());
    }

    @Test void shouldWriteFull() throws Exception {
        List<Cluster> clusters = ClusterTest.readClusterConfig().getClusters();
        givenClusters(clusters);

        assertThat(clusters).hasSize(4);
        Cluster a1 = clusters.get(0);
        assertThat(a1.id()).isEqualTo("server-a:1");
        ClusterNode a1_dev_1 = a1.getStages().get(0).index(a1, 1);
        ClusterNode a1_dev_2 = a1.getStages().get(0).index(a1, 2);
        ClusterNode a1_qa_1 = a1.getStages().get(1).index(a1, 1);
        ClusterNode a1_qa_2 = a1.getStages().get(1).index(a1, 2);
        ClusterNode a1_prod_1 = a1.getStages().get(2).index(a1, 1);
        ClusterNode a1_prod_2 = a1.getStages().get(2).index(a1, 2);
        ClusterNode a1_prod_3 = a1.getStages().get(2).index(a1, 3);

        Cluster a2 = clusters.get(1);
        assertThat(a2.id()).isEqualTo("server-a:2");

        Cluster b2 = clusters.get(2);
        assertThat(b2.id()).isEqualTo("server-b:2");
        ClusterNode b2_dev_1 = b2.getStages().get(0).index(b2, 1);
        ClusterNode b2_dev_2 = b2.getStages().get(0).index(b2, 2);

        Cluster l = clusters.get(3);
        assertThat(l.id()).isEqualTo("localhost:1");

        DeploymentBuilder foo = createDeployment("foo");
        DeploymentBuilder bar = createDeployment("bar");
        DeploymentBuilder baz = createDeployment("baz");

        List<Deployment> deployables = asList(
                foo.node(a1_dev_1).version("1.2.5-SNAPSHOT").build(),
                foo.node(a1_dev_2).version("1.2.5-SNAPSHOT").build(),
                foo.node(a1_qa_1).version("1.2.4").build(),
                foo.node(a1_qa_2).version("1.2.4").build(),
                foo.node(a1_prod_1).version("1.2.3").build(),
                foo.node(a1_prod_2).version("1.2.3").build(),
                foo.node(a1_prod_3).version("1.2.3").build(),
                bar.node(b2_dev_1).version("2.0.1").build(),
                bar.node(b2_dev_2).version("2.0.2").build(),
                baz.node(b2_dev_2).version("2.1.3").error("error-hint").build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        URL expected = DeploymentListHtmlMessageBodyWriterTest.class.getResource("deployment-list-full.html");
        assertThat(out.toString()).isEqualTo(contentOf(expected).trim());
    }
}
