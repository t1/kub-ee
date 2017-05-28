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
        Cluster cluster0 = writer.clusters.get(0);
        Stage stage0dev = cluster0.getStages().get(0);
        Stage stage0qa = cluster0.getStages().get(1);
        Stage stage0prod = cluster0.getStages().get(2);

        Cluster cluster1 = writer.clusters.get(1);
        Stage stage1dev = cluster1.getStages().get(0);

        DeploymentBuilder foo = createDeployment("foo").cluster(cluster0);
        DeploymentBuilder bar = createDeployment("bar").cluster(cluster1);
        List<Deployment> deployables = asList(
                foo.version("1.2.5-SNAPSHOT").stage(stage0dev).node(1).build(),
                foo.version("1.2.4").stage(stage0qa).node(1).build(),
                foo.version("1.2.3").stage(stage0prod).node(1).build(),
                foo.version("1.2.3").stage(stage0prod).node(2).build(),
                foo.version("1.2.3").stage(stage0prod).node(3).build(),
                bar.version("2.1.3").stage(stage1dev).node(1).error("error-hint").build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        assertThat(out.toString())
                .isEqualTo(contentOf(DeploymentListMessageBodyWriterTest.class.getResource("expected.html")));
    }
}
