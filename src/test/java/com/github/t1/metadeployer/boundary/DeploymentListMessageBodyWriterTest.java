package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.*;
import com.github.t1.metadeployer.model.Deployment.DeploymentBuilder;
import org.junit.*;

import java.io.*;
import java.util.List;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class DeploymentListMessageBodyWriterTest {
    private DeploymentListMessageBodyWriter writer = new DeploymentListMessageBodyWriter();

    @Before public void setUp() { writer.clusters = ClusterTest.readClusterConfig().clusters(); }

    private DeploymentBuilder createDeployment(String name) {
        return Deployment.builder()
                         .groupId(name + "-group")
                         .artifactId(name + "-art")
                         .type(name + "-type")
                         .name(name);
    }

    @Test
    public void shouldWriteHtml() throws Exception {
        Cluster my = writer.clusters.get(0);
        ClusterNode my_dev_1 = my.getStages().get(0).index(my, 1);
        ClusterNode my_qa_1 = my.getStages().get(1).index(my, 1);
        ClusterNode my_prod_1 = my.getStages().get(2).index(my, 1);
        ClusterNode my_prod_2 = my.getStages().get(2).index(my, 2);
        ClusterNode my_prod_3 = my.getStages().get(2).index(my, 3);

        Cluster other = writer.clusters.get(1);
        ClusterNode other_dev_1 = other.getStages().get(0).index(other, 1);
        ClusterNode other_dev_2 = other.getStages().get(0).index(other, 2);

        DeploymentBuilder foo = createDeployment("foo");
        DeploymentBuilder bar = createDeployment("bar");
        DeploymentBuilder baz = createDeployment("baz");
        List<Deployment> deployables = asList(
                foo.clusterNode(my_dev_1).version("1.2.5-SNAPSHOT").build(),
                foo.clusterNode(my_qa_1).version("1.2.4").build(),
                foo.clusterNode(my_prod_1).version("1.2.3").build(),
                foo.clusterNode(my_prod_2).version("1.2.3").build(),
                foo.clusterNode(my_prod_3).version("1.2.3").build(),
                bar.clusterNode(other_dev_1).version("2.0.1").build(),
                bar.clusterNode(other_dev_2).version("2.0.2").build(),
                baz.clusterNode(other_dev_2).version("2.1.3").error("error-hint").build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        assertThat(out.toString()).isEqualTo(
                contentOf(DeploymentListMessageBodyWriterTest.class.getResource("expected.html")).trim());
    }
}