package com.github.t1.metadeployer.boundary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;
import org.junit.Test;

import javax.ws.rs.client.ClientBuilder;
import java.util.List;

import static com.github.t1.metadeployer.boundary.TestData.*;
import static javax.ws.rs.core.MediaType.*;
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
        String response = ClientBuilder.newClient()
                                       .target("http://localhost:8080/meta-deployer")
                                       .request(TEXT_HTML_TYPE)
                                       .get(String.class);

        assertThat(response).isEqualTo("<html><head></head><body>"
                + "DeployerGateway.Deployable(name=deployer, groupId=unknown, artifactId=unknown, type=unknown, "
                + "version=unknown, error=empty checksum)"
                + "DeployerGateway.Deployable(name=meta-deployer, groupId=unknown, artifactId=unknown, type=unknown, "
                + "version=unknown, error=empty checksum)"
                + "</body></html>");
    }
}
