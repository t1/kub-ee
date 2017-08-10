package com.github.t1.kubee.boundary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.t1.kubee.AbstractIT;
import com.github.t1.kubee.model.*;
import com.github.t1.testtools.OrderedJUnitRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

@RunWith(OrderedJUnitRunner.class)
public class BoundaryIT extends AbstractIT {
    @Test
    public void shouldGetIndexAsJson() throws Exception {
        String response = kubEE().request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("{"
                + "\"load-balancers\":{"
                + /**/"\"href\":\"http://localhost:8080/api/load-balancers\","
                + /**/"\"title\":\"Load Balancers\"},"
                + "\"reverse-proxies\":{"
                + /**/"\"href\":\"http://localhost:8080/api/reverse-proxies\","
                + /**/"\"title\":\"Reverse Proxies\"},"
                + "\"clusters\":{"
                + /**/"\"href\":\"http://localhost:8080/api/clusters\","
                + /**/"\"title\":\"Clusters\"},"
                + "\"slots\":{"
                + /**/"\"href\":\"http://localhost:8080/api/slots\","
                + /**/"\"title\":\"Slots\"},"
                + "\"stages\":{"
                + /**/"\"href\":\"http://localhost:8080/api/stages\","
                + /**/"\"title\":\"Stages\"},"
                + "\"deployments\":{"
                + /**/"\"href\":\"http://localhost:8080/api/deployments\","
                + /**/"\"title\":\"Deployments\"}"
                + "}");
    }

    @Test
    public void shouldGetClustersAsJson() throws Exception {
        String response = kubEE().path("clusters").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo(""
                + "[{"
                + /**/"\"host\":\"localhost\","
                + /**/"\"slot\":{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + /**/"\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**//**/"\"count\":1,\"index-length\":0,\"loadBalancerConfig\":{}}]"
                + "},{"
                + /**/"\"host\":\"localhost\","
                + /**/"\"slot\":{\"name\":\"2\",\"http\":" + WORKER_2.baseUri().getPort() + ",\"https\":443},"
                + /**/"\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**//**/"\"count\":1,\"index-length\":0,\"loadBalancerConfig\":{}}]"
                + "}]");
    }

    @Test
    public void shouldGetOneClusterAsJson() throws Exception {
        String response = kubEE().path("clusters").path("localhost")
                                 .request(APPLICATION_JSON_TYPE)
                                 .get(String.class);

        assertThat(response).isEqualTo(""
                + "{"
                + "\"host\":\"localhost\","
                + "\"slot\":{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + "\"stages\":[{\"name\":\"PROD\",\"prefix\":\"\",\"suffix\":\"\",\"path\":\"application/\","
                + /**/"\"count\":1,\"index-length\":0,\"loadBalancerConfig\":{}}]"
                + "}");
    }

    @Test
    public void shouldGetSlotsAsJson() throws Exception {
        String response = kubEE().path("slots").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("["
                + "{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443},"
                + "{\"name\":\"2\",\"http\":" + WORKER_2.baseUri().getPort() + ",\"https\":443}"
                + "]");
    }

    @Test
    public void shouldGetOneSlotAsJson() throws Exception {
        String response = kubEE().path("slots").path("1")
                                 .request(APPLICATION_JSON_TYPE)
                                 .get(String.class);

        assertThat(response).isEqualTo("{\"name\":\"1\",\"http\":" + WORKER_1.baseUri().getPort() + ",\"https\":443}");
    }

    @Test
    public void shouldGetStagesAsJson() throws Exception {
        String response = kubEE().path("stages").request(APPLICATION_JSON_TYPE).get(String.class);

        assertThat(response).isEqualTo("[{"
                + "\"name\":\"PROD\","
                + "\"prefix\":\"\","
                + "\"suffix\":\"\","
                + "\"path\":\"application/\","
                + "\"count\":1,"
                + "\"index-length\":0,"
                + "\"loadBalancerConfig\":{}"
                + "}]");
    }

    @Test
    public void shouldGetOneStageAsJson() throws Exception {
        String response = kubEE().path("stages").path("PROD")
                                 .request(APPLICATION_JSON_TYPE)
                                 .get(String.class);

        assertThat(response).isEqualTo("{"
                + "\"name\":\"PROD\","
                + "\"prefix\":\"\","
                + "\"suffix\":\"\","
                + "\"path\":\"application/\","
                + "\"count\":1,"
                + "\"index-length\":0"
                + ",\"loadBalancerConfig\":{}"
                + "}");
    }

    @Test
    public void shouldGetDeploymentsAsJson() throws Exception {
        String response = kubEE().path("deployments").request(APPLICATION_JSON_TYPE).get(String.class);
        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        assertThat(list).contains(
                dummyDeployment(CLUSTER_1, "1.2.3"),
                dummyDeployment(CLUSTER_2, "1.2.4")
        );
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
        Response response = kubEE().path("deployments").request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th class=\"stage\" colspan=\"1\">PROD</th>")
                .contains("<th class=\"deployable-name\">dummy</th>");
    }
}
