package com.github.t1.metadeployer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.model.*;
import org.junit.Test;

import javax.ws.rs.client.*;
import javax.ws.rs.core.Response;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Cluster CLUSTER = Cluster.builder().host("localhost").port(8080)
                                                  .stage().name("PROD").count(1).prefix("").suffix("").add()
                                                  .build();
    private static final WebTarget META_DEPLOYER =
            ClientBuilder.newClient().target("http://localhost:8080/meta-deployer");

    @Test
    public void shouldGetAsJson() throws Exception {
        String response = META_DEPLOYER.request(APPLICATION_JSON_TYPE).get(String.class);

        List<Deployment> list = MAPPER.readValue(response, new TypeReference<List<Deployment>>() {});

        assertThat(list).contains(Deployment.builder()
                                            .groupId("unknown")
                                            .artifactId("unknown")
                                            .type("unknown")
                                            .version("unknown")
                                            .clusterNode(new ClusterNode(CLUSTER, CLUSTER.getStages().get(0), 1))
                                            .name("meta-deployer")
                                            .error("empty checksum")
                                            .build());
    }

    @Test
    public void shouldGetAsHtml() throws Exception {
        Response response = META_DEPLOYER.request(TEXT_HTML_TYPE).get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th colspan=\"3\">PROD</th>")
                .contains("<th>meta-deployer</th>");
    }
}
