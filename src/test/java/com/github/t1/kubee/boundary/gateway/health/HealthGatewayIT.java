package com.github.t1.kubee.boundary.gateway.health;

import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Cluster.HealthConfig;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import io.dropwizard.testing.junit5.DropwizardClientExtension;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthGatewayIT {
    @RegisterExtension static final DropwizardClientExtension SERVICE = new DropwizardClientExtension(HealthMockBoundary.class);

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

    @BeforeAll
    static void setUpClass() {
        Slot slot = Slot.builder().name("slot-1").http(SERVICE.baseUri().getPort()).build();
        HealthConfig healthConfig = HealthConfig.builder().path("-system/check").build();
        DEV = Stage.builder().name("DEV").build();
        CLUSTER = Cluster.builder().host("localhost").slot(slot).stage(DEV).healthConfig(healthConfig).build();
    }


    private HealthGateway gateway = new HealthGateway();

    @Test void shouldNotFetchHealthWithoutHealthConfig() {
        Cluster clusterWithoutConfig = CLUSTER.toBuilder().healthConfig(null).build();

        boolean healthy = gateway.fetch(clusterWithoutConfig.node(DEV, 1), "application");

        assertThat(healthy).isTrue();
    }

    @Test void shouldFetchHealth() {
        boolean healthy = gateway.fetch(CLUSTER.node(DEV, 1), "application");
        assertThat(healthy).isTrue();
    }

    @Test void shouldFetchUnhealthy() {
        boolean healthy;
        try {
            HealthGatewayIT.healthy = false;
            healthy = gateway.fetch(CLUSTER.node(DEV, 1), "application");
        } finally {
            HealthGatewayIT.healthy = true;
        }

        assertThat(healthy).isFalse();
    }
}
