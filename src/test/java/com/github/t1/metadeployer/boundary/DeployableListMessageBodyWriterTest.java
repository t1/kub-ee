package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.*;

import java.io.*;
import java.util.List;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;

public class DeployableListMessageBodyWriterTest {
    private DeployableListMessageBodyWriter writer = new DeployableListMessageBodyWriter();

    @Before
    public void setUp() throws Exception {
        ClusterConfig clusterConfig = new ClusterConfig();
        clusterConfig.readFrom(ClusterTest.class.getResourceAsStream("cluster-config.yaml"));
        writer.clusters = clusterConfig.clusters();
    }

    @Test
    public void shouldWrite() throws Exception {
        List<Deployable> deployables = asList(deployable().name("foo").build(), deployable().name("bar").build());
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
                + "    <th colspan=\"5\" class=\"stage\">DEV</th>\n"
                + "    <th colspan=\"1\" class=\"stage\">QA</th>\n"
                + "    <th colspan=\"3\" class=\"stage\">PROD</th>\n"
                + "</tr>\n"
                + "<tr>\n"
                + "    <th></th>\n"
                + "    <th></th>\n"
                + "    <th class=\"node\">01</th>\n"
                + "    <th class=\"node\">02</th>\n"
                + "    <th class=\"node\">01</th>\n"
                + "    <th class=\"node\">02</th>\n"
                + "    <th class=\"node\">01</th>\n"
                + "    <th class=\"node\">02</th>\n"
                + "    <th class=\"node\">03</th>\n"
                + "</tr>\n"
                + "</table>\n"
                + "</div>\n"
                + "</body>\n"
                + "</html>\n");
    }
}
