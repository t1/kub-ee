package com.github.t1.kubee.gateway.deployer;

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
        log.info("serve dummy deployable version {} on {}", dummyVersion, uriInfo.getRequestUri());
        return yaml(""
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
                + "");
    }

    private Response yaml(String entity) {
        return Response.ok(entity).type(MediaType.valueOf("application/yaml")).build();
    }

    @GET
    @Path("/repository/versions")
    public Response getVersions(@QueryParam("groupId") String groupId, @QueryParam("artifactId") String artifactId) {
        if ("dummy".equals(artifactId))
            return yaml(""
                    + "- 1.2.1\n"
                    + "- 1.2.2\n"
                    + "- " + dummyVersion + "\n");
        return yaml(""
                + "- 2.9.1\n"
                + "- 2.9.2\n"
                + "- 2.9.3\n");
    }
}
