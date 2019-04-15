package com.github.t1.kubee.boundary.gateway.container;

import com.github.t1.kubee.tools.ContainersFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.t1.kubee.tools.ContainersFixture.CLUSTER;
import static com.github.t1.kubee.tools.ContainersFixture.SLOT;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER1;
import static com.github.t1.kubee.tools.ContainersFixture.WORKER2;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class ClusterStatusGatewayTest {
    @RegisterExtension ContainersFixture containers = new ContainersFixture().setPort(SLOT.getHttp());

    @Test void shouldFailToReadClusterStatusWithoutDockerComposeDir() {
        ClusterStatusGateway gateway = new ClusterStatusGateway(null);

        Throwable throwable = catchThrowable(() -> gateway.clusterStatus(CLUSTER));

        assertThat(throwable).hasMessage("no docker compose dir configured");
    }

    @Test void shouldReadClusterStatus() {
        containers.givenEndpoints(WORKER1, WORKER2);
        ClusterStatusGateway gateway = new ClusterStatusGateway(containers.getDockerComposeDir());

        ClusterStatus clusterStatus = gateway.clusterStatus(CLUSTER);

        assertThat(clusterStatus.endpoints()).containsExactly(WORKER1, WORKER2);
    }
}
