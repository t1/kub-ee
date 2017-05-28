package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.boundary.model.ClusterTest;
import com.github.t1.metadeployer.model.Deployment;
import org.junit.*;

import java.io.*;
import java.util.List;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class DeploymentListMessageBodyWriterTest {
    private DeploymentListMessageBodyWriter writer = new DeploymentListMessageBodyWriter();

    @Before public void setUp() { writer.clusters = ClusterTest.readClusterConfig().clusters(); }

    @Test
    public void shouldWriteHtml() throws Exception {
        List<Deployment> deployables = asList(
                unknownDeployment().name("foo").build(),
                unknownDeployment().name("bar").error("error-hint").build());
        OutputStream out = new ByteArrayOutputStream();

        writer.writeTo(deployables, null, null, null, null, null, out);

        assertThat(out.toString()).isEqualTo("<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head>\n"
                + "    <title>Meta-Deployer</title>\n"
                + "    <meta charset=\"utf-8\" />\n"
                + "    <link rel='stylesheet' href=\"http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css\" />\n"
                + "    <link rel='stylesheet' href=\"../style.css\" />\n"
                + "</head>\n"
                + "<body>\n"
                + "<div class=\"table-responsive\">\n"
                + "<table class=\"table table-striped service-table\">\n"
                + "<tr>\n"
                + "    <th>Cluster</th>\n"
                + "    <th>Application</th>\n"
                + "    <th colspan=\"2\" class=\"stage\">DEV</th>\n"
                + "    <th colspan=\"1\" class=\"stage\">QA</th>\n"
                + "    <th colspan=\"3\" class=\"stage\">PROD</th>\n"
                + "    <th colspan=\"1\" class=\"stage\"></th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "    <th></th>\n"
                + "    <th></th>\n"
                + "    <th class=\"node\">01</th>\n"
                + "    <th class=\"node\"></th>\n"
                + "    <th class=\"node\">01</th>\n"
                + "    <th class=\"node\">02</th>\n"
                + "    <th class=\"node\">03</th>\n"
                + "    <th class=\"node\">1</th>\n"
                + "    <th class=\"node\">2</th>\n"
                + "    <th class=\"node\"></th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='cluster' rowspan='2'>my.boss</th>\n"
                + "  <th class='service'>foo</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='service'>bar</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='cluster' rowspan='2'>other.boss</th>\n"
                + "  <th class='service'>foo</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='service'>bar</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='cluster' rowspan='2'>third.boss</th>\n"
                + "  <th class='service'>foo</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "  <th class='service'>bar</th>\n"
                + "</tr>\n"
                + "</table>\n"
                + "</div>\n"
                + "</body>\n"
                + "</html>\n");
    }
}
