package com.github.t1.metadeployer.gateway;

import javax.ws.rs.*;
import javax.ws.rs.core.*;

@Path("/")
public class DeployerMock {
    @GET public Response get() {
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
                + "    version: 1.2.3\n"
                + "    type: war\n"
                + "").type(MediaType.valueOf("application/yaml")).build();
    }
}
