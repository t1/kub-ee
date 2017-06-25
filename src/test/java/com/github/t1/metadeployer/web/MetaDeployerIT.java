package com.github.t1.metadeployer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.boundary.ClusterConfig;
import com.github.t1.metadeployer.gateway.DeployerMock;
import com.github.t1.metadeployer.model.*;
import com.github.t1.testtools.*;
import io.dropwizard.testing.junit.DropwizardClientRule;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.io.File;
import java.nio.file.*;
import java.util.List;

import static com.github.t1.testtools.AbstractPage.*;
import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Stage PROD = Stage.builder().name("PROD").path("application/").count(1).build();

    private static final Path CLUSTER_CONFIG_PATH = Paths.get("target/meta-deployer-it-cluster-config.yaml");

    @ClassRule public static final DropwizardClientRule WORKER_1
            = new DropwizardClientRule(new DeployerMock("1.2.3"));
    @ClassRule public static final DropwizardClientRule WORKER_2
            = new DropwizardClientRule(new DeployerMock("1.2.4"));

    private static Cluster CLUSTER_1;
    private static Cluster CLUSTER_2;

    @ClassRule public static final WildflySwarmTestRule MASTER = new WildflySwarmTestRule()
            .withProperty("meta-deployer.cluster-config", CLUSTER_CONFIG_PATH);

    @ClassRule public static final WebDriverRule DRIVER = new WebDriverRule(new FirefoxDriver());

    private static DeploymentsPage deployments;

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

        MASTER.deploy(ShrinkWrap.createFromZipFile(WebArchive.class, new File("target/meta-deployer.war")));
        deployments = new DeploymentsPage(DRIVER, MASTER.baseUri().resolve("/api/deployments"));
    }


    private WebTarget metaDeployer() { return ClientBuilder.newClient().target(MASTER.baseUri()).path("/api"); }

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
                + /**/"\"slot\":{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + /**/"\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**//**/"\"count\":1,\"indexLength\":0}]"
                + "},{"
                + /**/"\"host\":\"localhost\","
                + /**/"\"slot\":{\"name\":\"2\",\"http\":" + WORKER_2.baseUri().getPort() + ",\"https\":443},"
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
                + "\"slot\":{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + "\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**/"\"count\":1,\"indexLength\":0}]"
                + "}");
    }

    @Test
    public void shouldGetSlotsAsJson() throws Exception {
        String response = metaDeployer().path("slots").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("["
                + "{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + "{\"name\":\"2\",\"http\":" + WORKER_2.baseUri().getPort() + ",\"https\":443}"
                + "]");
    }

    @Test
    public void shouldGetOneSlotAsJson() throws Exception {
        String response = metaDeployer().path("slots").path("1")
                                        .request(APPLICATION_JSON_TYPE)
                                        .get(String.class);

        assertThat(response).isEqualTo("{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443}");
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

        assertThat(list).contains(dummyDeployment(CLUSTER_1, "1.2.3"), dummyDeployment(CLUSTER_2, "1.2.4"));
    }

    private Deployment dummyDeployment(Cluster cluster, String version) {
        return Deployment.builder()
                         .groupId("com.github.t1")
                         .artifactId("dummy")
                         .type("war")
                         .version(version)
                         .node(new ClusterNode(cluster, cluster.getStages().get(0), 1))
                         .name("dummy")
                         .build();
    }

    @Test
    public void shouldGetDeploymentsAsHtml() throws Exception {
        Response response = metaDeployer().path("deployments").request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th class=\"stage\" colspan=\"1\">PROD</th>")
                .contains("<th class=\"deployable-name\">dummy</th>");
    }

    @Test
    public void shouldGetDeploymentsPage() throws Exception {
        deployments.navigateTo();

        deployments.assertOpen();
        WebElement deployment = deployments.findDeployment(By.id("localhost:1:PROD:1:dummy"));
        WebElement dropdown = deployment.findElement(By.className("dropdown"));
        WebElement toggle = dropdown.findElement(By.className("dropdown-toggle"));
        WebElement menu = dropdown.findElement(By.className("versions-menu"));

        assertThat(dropdown).has(not(cssClass("open")));
        assertThat(menu).is(not(displayed()));

        toggle.click();

        assertThat(dropdown).has(cssClass("open"));
        assertThat(dropdown).is(displayed());

        WebElement versionsUL = menu.findElement(By.tagName("ul"));
        assertThat(versionsUL).has(cssClass("list-unstyled"));

        List<WebElement> versionLis = versionsUL.findElements(By.tagName("li"));
        assertThat(versionLis).hasSize(3);
        assertVersionLi(versionLis.get(0), "refresh", "1.3.3", "undeployee");
        assertVersionLi(versionLis.get(1), "refresh", "1.3.4", "undeploying");
        assertVersionLi(versionLis.get(2), "refresh", "1.3.5", "deploying");
    }

    private void assertVersionLi(WebElement versionLi, String iconName, String versionName, String status) {
        WebElement iconSpan = versionLi.findElement(By.cssSelector("span.version-icon"));
        assertThat(iconSpan).has(cssClass("glyphicon", "glyphicon-" + iconName));
        assertThat(iconSpan).has(cssClass("version-icon-" + status));

        WebElement labelSpan = versionLi.findElement(By.cssSelector("span.version"));
        assertThat(labelSpan).has(text(versionName));
    }
}
