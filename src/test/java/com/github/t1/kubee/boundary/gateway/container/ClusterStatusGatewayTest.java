package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.tools.ContainersFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.t1.kubee.TestData.CLUSTER;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT1;
import static com.github.t1.kubee.TestData.PROD_ENDPOINT2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClusterStatusGatewayTest {
    @RegisterExtension ContainersFixture containers = new ContainersFixture();

    @Test void shouldFailToReadClusterStatusWithoutDockerComposeDir() {
        ClusterStatusGateway gateway = new ClusterStatusGateway(null);

        Throwable throwable = catchThrowable(() -> gateway.clusterStatus(CLUSTER));

        assertThat(throwable).isExactlyInstanceOf(RuntimeException.class)
            .hasMessage("no docker compose dir configured");
    }

    @Test void shouldReadClusterStatus() {
        containers.given(PROD_ENDPOINT1, PROD_ENDPOINT2);
        ClusterStatusGateway gateway = new ClusterStatusGateway(containers.getDockerComposeDir());

        ClusterStatus clusterStatus = gateway.clusterStatus(CLUSTER);

        assertThat(clusterStatus.endpoints()).containsExactly(PROD_ENDPOINT1, PROD_ENDPOINT2);
    }
}
