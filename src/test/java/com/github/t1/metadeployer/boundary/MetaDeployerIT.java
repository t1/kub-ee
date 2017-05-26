package com.github.t1.metadeployer.boundary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.List;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static javax.ws.rs.core.MediaType.*;
import static javax.ws.rs.core.Response.Status.*;
import static org.assertj.core.api.Assertions.*;

public class MetaDeployerIT {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    public void shouldGetAsJson() throws Exception {
        String response = ClientBuilder.newClient()
                                       .target("http://localhost:8080/meta-deployer")
                                       .request(APPLICATION_JSON_TYPE)
                                       .get(String.class);

        List<Deployable> list = MAPPER.readValue(response, new TypeReference<List<Deployable>>() {});
        assertThat(list).containsOnly(
                emptyChecksumDeployable("deployer"),
                emptyChecksumDeployable("meta-deployer")
        );
    }

    @Test
    public void shouldGetAsHtml() throws Exception {
        Response response = ClientBuilder.newClient()
                                         .target("http://localhost:8080/meta-deployer")
                                         .request(TEXT_HTML_TYPE)
                                         .get();

        assertThat(response.getStatusInfo()).isEqualTo(OK);
        assertThat(response.readEntity(String.class))
                .contains("<th colspan=\"3\" class=\"stage\">PROD</th>")
                .contains("<th class='service'>meta-deployer</th>");
    }
}
