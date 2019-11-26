package com.github.t1.kubee.boundary.gateway.health;

import com.github.t1.jaxrsclienttest.JaxRsTestExtension;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Cluster.HealthConfig;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthGatewayIT {
    @RegisterExtension
    static final JaxRsTestExtension SERVICE = new JaxRsTestExtension(new HealthMockBoundary());

    private static boolean healthy = true;

    @Path("/system")
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
        Slot slot = Slot.named("slot-1").withHttp(SERVICE.baseUri().getPort());
        HealthConfig healthConfig = HealthConfig.builder().path("/check").build();
        DEV = Stage.builder().name("DEV").build();
        CLUSTER = Cluster.builder().host("localhost").slot(slot).stage(DEV).healthConfig(healthConfig).build();
    }


    private HealthGateway gateway = new HealthGateway();

    @Test void shouldNotFetchHealthWithoutHealthConfig() {
        Cluster clusterWithoutConfig = CLUSTER.toBuilder().healthConfig(null).build();

        boolean healthy = gateway.fetch(clusterWithoutConfig.node(DEV, 1), "/system");

        assertThat(healthy).isTrue();
    }

    @Test void shouldFetchHealth() {
        boolean healthy = gateway.fetch(CLUSTER.node(DEV, 1), "/system");

        assertThat(healthy).isTrue();
    }

    @Test void shouldFetchUnhealthy() {
        boolean healthy;
        try {
            HealthGatewayIT.healthy = false;
            healthy = gateway.fetch(CLUSTER.node(DEV, 1), "/system");
        } finally {
            HealthGatewayIT.healthy = true;
        }

        assertThat(healthy).isFalse();
    }
}
