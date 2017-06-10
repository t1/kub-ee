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
import java.net.URI;
import java.nio.file.*;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final WebDriverRule driver = new WebDriverRule();

    private static Cluster CLUSTER;

    private static final String CLUSTER_CONFIG = "target/meta-deployer-it-cluster-config.yaml";

    @ClassRule public static DropwizardClientRule worker = new DropwizardClientRule(DeployerMock.class);

    @ClassRule public static WildflySwarmTestRule master = new WildflySwarmTestRule()
            .withProperty("meta-deployer.cluster-config", CLUSTER_CONFIG);

    private static ApplicationsPage applications;

    @BeforeClass @SneakyThrows public static void setup() {
        URI deployerBase = worker.baseUri();
        CLUSTER = Cluster.builder()
                         .host(deployerBase.getHost())
                         .slot(Slot.builder().name("0").http(deployerBase.getPort()).build())
                         .stage().name("PROD").prefix("").suffix("").count(1).path("application/").add()
                         .build();
        Files.write(Paths.get(CLUSTER_CONFIG), CLUSTER.toYaml().getBytes());
        master.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/meta-deployer.war")));
        applications = new ApplicationsPage(driver, master.baseUri().resolve("/api/applications"));
    }


    private WebTarget metaDeployer() { return ClientBuilder.newClient().target(master.baseUri()).path("/api"); }

    @Test
    public void shouldGetIndexAsJson() throws Exception {
        String response = metaDeployer().request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("{"
                + "\"clusters\":\"http://localhost:8080/api/clusters\","
                + "\"applications\":\"http://localhost:8080/api/applications\""
                + "}");
    }

    @Test
    public void shouldGetApplicationsAsJson() throws Exception {
        String response = metaDeployer().path("applications").request(APPLICATION_JSON_TYPE).get(String.class);
        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        Deployment expected = Deployment.builder()
                                        .groupId("com.github.t1")
                                        .artifactId("dummy")
                                        .type("war")
                                        .version("1.2.3")
                                        .node(new ClusterNode(CLUSTER, CLUSTER.getStages().get(0), 1))
                                        .name("dummy")
                                        .build();
        assertThat(list).contains(expected);
    }

    @Test
    public void shouldGetApplicationsAsHtml() throws Exception {
        Response response = metaDeployer().path("applications").request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th class=\"stage\" colspan=\"1\">PROD</th>")
                .contains("<th class=\"app\">dummy</th>");
    }

    @Test
    public void shouldGetApplicationsPage() throws Exception {
        applications.navigateTo();

        applications.assertOpen();
    }
}
