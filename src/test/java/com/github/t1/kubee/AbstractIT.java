package com.github.t1.kubee;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.kubee.boundary.ClusterConfig;
import com.github.t1.kubee.gateway.deployer.DeployerMock;
import com.github.t1.kubee.model.*;
import com.github.t1.testtools.WildflySwarmTestRule;
import io.dropwizard.testing.junit.DropwizardClientRule;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;

import javax.ws.rs.client.*;
import java.io.File;
import java.nio.file.*;

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
            .withProperty(ClusterConfig.FILE_NAME_PROPERTY, CLUSTER_CONFIG_PATH);

    @BeforeClass public static void setup() {
        CLUSTER_1 = Cluster.builder()
                           .host(WORKER_1.baseUri().getHost())
                           .slot(Slot.builder().name("1").http(WORKER_1.baseUri().getPort()).build())
                           .stage(PROD)
                           .build();
        CLUSTER_2 = Cluster.builder()
                           .host(WORKER_2.baseUri().getHost())
                           .slot(Slot.builder().name("2").http(WORKER_2.baseUri().getPort()).build())
                           .stage(PROD)
                           .build();
        new ClusterConfig().add(CLUSTER_1).add(CLUSTER_2).write(CLUSTER_CONFIG_PATH);

        MASTER.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/kub-ee.war")));
    }

    protected WebTarget kubEE() { return ClientBuilder.newClient().target(MASTER.baseUri()).path("/api"); }
}
