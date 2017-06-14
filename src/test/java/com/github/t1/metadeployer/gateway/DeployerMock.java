package com.github.t1.metadeployer.gateway;

import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Slf4j
@Path("/")
public class DeployerMock {
    private final String dummyVersion;

    public DeployerMock() { this("1.2.3"); }

    public DeployerMock(String dummyVersion) { this.dummyVersion = dummyVersion; }

    @GET public Response get(@Context UriInfo uriInfo) {
        log.info("serve dummy deployable version {} for {}", dummyVersion, uriInfo.getRequestUri());
        return Response.ok(""
                + "deployables:\n"
                + "  deployer:\n"
                + "    group-id: com.github.t1\n"
                + "    artifact-id: deployer\n"
                + "    version: 2.9.2\n"
                + "    checksum: cda40760995291073825e393bd5bdc17a7bcd6f3\n"
                + "    type: war\n"
                + "  dummy:\n"
                + "    group-id: com.github.t1\n"
                + "    artifact-id: dummy\n"
                + "    version: " + dummyVersion + "\n"
                + "    type: war\n"
                + "").type(MediaType.valueOf("application/yaml")).build();
    }
}
