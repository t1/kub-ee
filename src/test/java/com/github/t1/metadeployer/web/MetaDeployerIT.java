package com.github.t1.metadeployer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.boundary.ClusterConfig;
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
import java.nio.file.Paths;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final WebDriverRule driver = new WebDriverRule();
    private static final Stage PROD = Stage.builder().name("PROD").path("application/").count(1).build();

    private static Cluster CLUSTER_1;
    private static Cluster CLUSTER_2;

    private static final String CLUSTER_CONFIG = "target/meta-deployer-it-cluster-config.yaml";

    @ClassRule public static DropwizardClientRule worker1 = new DropwizardClientRule(DeployerMock.class);
    @ClassRule public static DropwizardClientRule worker2 = new DropwizardClientRule(DeployerMock.class);

    @ClassRule public static WildflySwarmTestRule master = new WildflySwarmTestRule()
            .withProperty("meta-deployer.cluster-config", CLUSTER_CONFIG);

    private static DeploymentsPage deployments;

    @BeforeClass @SneakyThrows public static void setup() {
        CLUSTER_1 = Cluster.builder()
                           .host(worker1.baseUri().getHost())
                           .slot(Slot.builder().name("1").http(worker1.baseUri().getPort()).build())
                           .stage(PROD)
                           .build();
        CLUSTER_2 = Cluster.builder()
                           .host(worker2.baseUri().getHost())
                           .slot(Slot.builder().name("2").http(worker2.baseUri().getPort()).build())
                           .stage(PROD)
                           .build();
        ClusterConfig clusters = new ClusterConfig();
        clusters.clusters().add(CLUSTER_1);
        clusters.clusters().add(CLUSTER_2);
        clusters.write(Paths.get(CLUSTER_CONFIG));

        master.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/meta-deployer.war")));
        deployments = new DeploymentsPage(driver, master.baseUri().resolve("/api/deployments"));
    }


    private WebTarget metaDeployer() { return ClientBuilder.newClient().target(master.baseUri()).path("/api"); }

    @Test
    public void shouldGetIndexAsJson() throws Exception {
        String response = metaDeployer().request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("{"
                + "\"clusters\":\"http://localhost:8080/api/clusters\","
                + "\"slots\":\"http://localhost:8080/api/slots\","
                + "\"stages\":\"http://localhost:8080/api/stages\","
                + "\"deployments\":\"http://localhost:8080/api/deployments\""
                + "}");
    }

    @Test
    public void shouldGetClustersAsJson() throws Exception {
        String response = metaDeployer().path("clusters").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo(""
                + "[{"
                + /**/"\"host\":\"localhost\","
                + /**/"\"slot\":{\"name\":\"1\",\"http\":" + worker1.baseUri().getPort() + ",\"https\":443},"
                + /**/"\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**//**/"\"count\":1,\"indexLength\":0}]"
                + "},{"
                + /**/"\"host\":\"localhost\","
                + /**/"\"slot\":{\"name\":\"2\",\"http\":" + worker2.baseUri().getPort() + ",\"https\":443},"
                + /**/"\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**//**/"\"count\":1,\"indexLength\":0}]"
                + "}]");
    }

    @Test
    public void shouldGetOneClusterAsJson() throws Exception {
        String response = metaDeployer().path("clusters").path("localhost")
                                        .request(APPLICATION_JSON_TYPE)
                                        .get(String.class);

        assertThat(response).isEqualTo(""
                + "{"
                + "\"host\":\"localhost\","
                + "\"slot\":{\"name\":\"1\",\"http\":" + worker1.baseUri().getPort() + ",\"https\":443},"
                + "\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**/"\"count\":1,\"indexLength\":0}]"
                + "}");
    }

    @Test
    public void shouldGetSlotsAsJson() throws Exception {
        String response = metaDeployer().path("slots").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("["
                + "{\"name\":\"1\",\"http\":" + worker1.baseUri().getPort() + ",\"https\":443},"
                + "{\"name\":\"2\",\"http\":" + worker2.baseUri().getPort() + ",\"https\":443}"
                + "]");
    }

    @Test
    public void shouldGetOneSlotAsJson() throws Exception {
        String response = metaDeployer().path("slots").path("1")
                                        .request(APPLICATION_JSON_TYPE)
                                        .get(String.class);

        assertThat(response).isEqualTo("{\"name\":\"1\",\"http\":" + worker1.baseUri().getPort() + ",\"https\":443}");
    }

    @Test
    public void shouldGetStagesAsJson() throws Exception {
        String response = metaDeployer().path("stages").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("[{"
                + "\"name\":\"PROD\","
                + "\"prefix\":\"\","
                + "\"suffix\":\"\","
                + "\"path\":\"application/\","
                + "\"count\":1,"
                + "\"indexLength\":0"
                + "}]");
    }

    @Test
    public void shouldGetOneStageAsJson() throws Exception {
        String response = metaDeployer().path("stages").path("PROD")
                                        .request(APPLICATION_JSON_TYPE)
                                        .get(String.class);

        assertThat(response).isEqualTo("{"
                + "\"name\":\"PROD\","
                + "\"prefix\":\"\","
                + "\"suffix\":\"\","
                + "\"path\":\"application/\","
                + "\"count\":1,"
                + "\"indexLength\":0"
                + "}");
    }

    @Test
    public void shouldGetDeploymentsAsJson() throws Exception {
        String response = metaDeployer().path("deployments").request(APPLICATION_JSON_TYPE).get(String.class);
        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        Deployment expected = Deployment.builder()
                                        .groupId("com.github.t1")
                                        .artifactId("dummy")
                                        .type("war")
                                        .version("1.2.3")
                                        .node(new ClusterNode(CLUSTER_1, CLUSTER_1.getStages().get(0), 1))
                                        .name("dummy")
                                        .build();
        assertThat(list).contains(expected);
    }

    @Test
    public void shouldGetDeploymentsAsHtml() throws Exception {
        Response response = metaDeployer().path("deployments").request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th class=\"stage\" colspan=\"1\">PROD</th>")
                .contains("<th class=\"app\">dummy</th>");
    }

    @Test
    public void shouldGetDeploymentsPage() throws Exception {
        deployments.navigateTo();

        deployments.assertOpen();
    }
}
