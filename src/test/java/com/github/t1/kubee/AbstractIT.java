package com.github.t1.kubee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.kubee.control.ClusterConfig;
import com.github.t1.kubee.gateway.deployer.DeployerMock;
import com.github.t1.kubee.model.Cluster;
import com.github.t1.kubee.model.ClusterTest;
import com.github.t1.kubee.model.Stage;
import com.github.t1.testtools.WildflySwarmTestRule;
import io.dropwizard.testing.junit.DropwizardClientRule;
import lombok.SneakyThrows;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;

import static com.github.t1.kubee.control.ClusterConfig.FILE_NAME_PROPERTY;
import static java.nio.charset.StandardCharsets.UTF_8;

public abstract class AbstractIT {
    protected static final ObjectMapper MAPPER = new ObjectMapper();
    protected static final Stage PROD = Stage.builder().name("PROD").path("application/").count(1).build();
    private static final Path CLUSTER_CONFIG_PATH = Paths.get("target/kub-ee-it-cluster-config.yaml");

    @ClassRule public static final DropwizardClientRule WORKER_1
        = new DropwizardClientRule(new DeployerMock("1.2.3"));
    @ClassRule public static final DropwizardClientRule WORKER_2
        = new DropwizardClientRule(new DeployerMock("1.2.4"));

    protected static Cluster CLUSTER_1;
    protected static Cluster CLUSTER_2;

    @ClassRule public static final WildflySwarmTestRule MASTER = new WildflySwarmTestRule()
        .withProperty(FILE_NAME_PROPERTY, CLUSTER_CONFIG_PATH);

    @BeforeClass public static void setup() throws IOException {
        new Template(ClusterTest.class.getResourceAsStream("it-cluster-config.yaml"))
            .fill("slot-1-port", WORKER_1.baseUri().getPort())
            .fill("slot-2-port", WORKER_2.baseUri().getPort())
            .write(CLUSTER_CONFIG_PATH);

        new ClusterConfig();
        List<Cluster> clusters = ClusterConfig.readFrom(Files.newInputStream(CLUSTER_CONFIG_PATH));
        CLUSTER_1 = clusters.get(0);
        CLUSTER_2 = clusters.get(1);

        MASTER.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/kub-ee.war")));
    }

    protected WebTarget kubEE() { return ClientBuilder.newClient().target(MASTER.baseUri()).path("/api"); }

    private static class Template {
        private String template;

        private Template(InputStream inputStream) {
            this.template = new Scanner(inputStream).useDelimiter("\\Z").next();
        }

        private Template fill(String field, int value) { return fill(field, Integer.toString(value)); }

        private Template fill(String field, String value) {
            this.template = template.replace("${" + field + "}", value);
            return this;
        }

        @SneakyThrows(IOException.class)
        private void write(Path path) { Files.write(path, template.getBytes(UTF_8)); }
    }
}
