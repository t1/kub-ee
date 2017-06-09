package com.github.t1.metadeployer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.gateway.DeployerMock;
import com.github.t1.metadeployer.model.*;
import com.github.t1.testtools.*;
import io.dropwizard.testing.junit.DropwizardClientRule;
import lombok.SneakyThrows;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.io.File;
import java.nio.file.*;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Cluster CLUSTER;

    private static final String CLUSTER_CONFIG = "target/meta-deployer-it-cluster-config.yaml";

    @ClassRule public static DropwizardClientRule worker = new DropwizardClientRule(DeployerMock.class);

    @ClassRule public static WildflySwarmTestRule master = new WildflySwarmTestRule()
            .withProperty("meta-deployer.cluster-config", CLUSTER_CONFIG);

    @BeforeClass @SneakyThrows public static void setup() {
        CLUSTER = Cluster.builder()
                         .host(worker.baseUri().getHost())
                         .slot(Slot.builder().name("0").http(worker.baseUri().getPort()).build())
                         .stage().name("PROD").prefix("").suffix("").count(1).deployerPath("application/").add()
                         .build();
        Files.write(Paths.get(CLUSTER_CONFIG), CLUSTER.toYaml().getBytes());
        master.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/meta-deployer.war")));
    }

    // @ClassRule public static final WebDriverRule driver = Pages.driver;

    private WebTarget metaDeployer() { return ClientBuilder.newClient().target(master.baseUri()).path("/api"); }

    @Test
    public void shouldGetAsJson() throws Exception {
        String response = metaDeployer().request(APPLICATION_JSON_TYPE).get(String.class);

        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        Deployment expected = Deployment.builder()
                                        .groupId("com.github.t1")
                                        .artifactId("dummy")
                                        .type("war")
                                        .version("1.2.3")
                                        .clusterNode(new ClusterNode(CLUSTER, CLUSTER.getStages().get(0), 1))
                                        .name("dummy")
                                        .build();
        assertThat(list).contains(expected);
    }

    @Test
    public void shouldGetAsHtml() throws Exception {
        Response response = metaDeployer().request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th class=\"stage\" colspan=\"1\">PROD</th>")
                .contains("<th class=\"app\">dummy</th>");
    }
}
