package com.github.t1.metadeployer.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.model.*;
import com.github.t1.testtools.*;
import org.junit.*;
import org.junit.runner.RunWith;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.util.List;

import static com.github.t1.metadeployer.model.ClusterTest.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@Ignore
@RunWith(OrderedJUnitRunner.class)
public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Cluster CLUSTER = Cluster.builder().host("localhost").slot(SLOT_0)
                                                  .stage().name("PROD").count(1).prefix("").suffix("").add()
                                                  .build();

    // @ClassRule public static DropwizardClientRule deployer = new DropwizardClientRule(DeployerMock.class);

    // @ClassRule public static DropwizardClientRule metaDeployer = new DropwizardClientRule(JaxRs.class, Boundary.class);

    @ClassRule public static final WebDriverRule driver = Pages.driver;

    private WebTarget metaDeployer() { return null; } //ClientBuilder.newClient().target(metaDeployer.baseUri()).path("/api"); }

    @Test
    public void shouldGetAsJson() throws Exception {
        String response = metaDeployer().request(APPLICATION_JSON_TYPE).get(String.class);

        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        System.out.println(list.stream().map(Deployment::toString).collect(joining("\n")));
        Deployment expected = Deployment.builder()
                                        .groupId("unknown")
                                        .artifactId("unknown")
                                        .type("unknown")
                                        .version("unknown")
                                        .clusterNode(new ClusterNode(CLUSTER, CLUSTER.getStages().get(0), 1))
                                        .name("meta-deployer")
                                        .error("empty checksum")
                                        .build();
        assertThat(list).contains(expected);
    }

    @Test
    public void shouldGetAsHtml() throws Exception {
        Response response = metaDeployer().request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        String html = response.readEntity(String.class);
        System.out.println(html);
        assertThat(html)
                .contains("<th class=\"stage\" colspan=\"3\">PROD</th>")
                .contains("<th class=\"app\">meta-deployer</th>");
    }
}
