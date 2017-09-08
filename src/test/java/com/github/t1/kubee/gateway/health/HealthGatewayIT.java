package com.github.t1.kubee.gateway.health;

import com.github.t1.kubee.model.*;
import com.github.t1.kubee.model.Cluster.HealthConfig;
import io.dropwizard.testing.junit.DropwizardClientRule;
import org.junit.*;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;

import static org.junit.Assert.*;

public class HealthGatewayIT {
    @ClassRule public static final DropwizardClientRule SERVICE = new DropwizardClientRule(HealthMockBoundary.class);

    private static boolean healthy = true;

    @Path("/-system")
    public static class HealthMockBoundary {
        @GET @Path("/check") public Response check() {
            return healthy
                    ? Response.ok("okay").build()
                    : Response.serverError().entity("not okay").build();
        }
    }

    private static Stage DEV;
    private static Cluster CLUSTER;

    @BeforeClass
    public static void setUpClass() throws Exception {
        Slot slot = Slot.builder().name("slot-1").http(SERVICE.baseUri().getPort()).build();
        HealthConfig healthConfig = HealthConfig.builder().path("-system/check").build();
        DEV = Stage.builder().name("DEV").build();
        CLUSTER = Cluster.builder().host("localhost").slot(slot).stage(DEV).healthConfig(healthConfig).build();
    }


    private HealthGateway gateway = new HealthGateway();

    @Test
    public void shouldNotFetchHealthWithoutHealthConfig() throws Exception {
        Cluster clusterWithoutConfig = CLUSTER.toBuilder().healthConfig(null).build();

        boolean healthy = gateway.fetch(clusterWithoutConfig.node(DEV, 1), "application");

        assertTrue(healthy);
    }

    @Test
    public void shouldFetchHealth() throws Exception {
        boolean healthy = gateway.fetch(CLUSTER.node(DEV, 1), "application");
        assertTrue(healthy);
    }

    @Test
    public void shouldFetchUnhealthy() throws Exception {
        boolean healthy;
        try {
            HealthGatewayIT.healthy = false;
            healthy = gateway.fetch(CLUSTER.node(DEV, 1), "application");
        } finally {
            HealthGatewayIT.healthy = true;
        }

        assertFalse(healthy);
    }
}
