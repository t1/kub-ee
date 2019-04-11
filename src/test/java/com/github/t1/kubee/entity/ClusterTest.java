package com.github.t1.kubee.entity;

import com.github.t1.kubee.boundary.gateway.Clusters;
import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.entity.Cluster.HealthConfig;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static com.github.t1.kubee.entity.VersionStatus.unbalanced;
import static org.assertj.core.api.Assertions.assertThat;

public class ClusterTest {
    public static final Slot SLOT_0 = Slot.builder().name("0").http(8080).https(8443).build();
    public static final Slot SLOT_1 = Slot.builder().name("1").http(8180).https(8543).build();
    private static final Slot SLOT_2 = Slot.builder().name("2").http(8280).https(8643).build();

    private static final HealthConfig HEALTH_CONFIG = HealthConfig.builder().path("dummy/health/path").build();

    public static final Stage DEV = Stage.builder().name("DEV").prefix("").suffix("dev").count(2).indexLength(2)
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();
    private static final Stage QA = Stage.builder().name("QA").prefix("qa").suffix("").count(2).indexLength(2)
        .loadBalancerConfig("reload", "service")
        .loadBalancerConfig("port", "12345")
        .build();
    private static final Stage PROD = Stage.builder().name("PROD").prefix("").suffix("").count(3).indexLength(2)
        .loadBalancerConfig("reload", "direct").build();

    public static final Cluster[] CLUSTERS = {
        Cluster.builder().host("server-a.server.lan").slot(SLOT_1)
            .stage(DEV)
            .stage(QA)
            .stage(PROD)
            .healthConfig(HEALTH_CONFIG).build(),
        Cluster.builder().host("server-a.server.lan").slot(SLOT_2)
            .stage(DEV)
            .stage(QA)
            .stage(PROD)
            .healthConfig(HEALTH_CONFIG).build(),
        Cluster.builder().host("server-b.server.lan").slot(SLOT_2)
            .stage().name("DEV").prefix("").suffix("test").count(2).indexLength(2)
            .loadBalancerConfig("reload", "set-user-id-script")
            .add()
            .stage(QA)
            .stage().name("PROD").count(5).indexLength(2)
            .loadBalancerConfig("reload", "docker-kill-hup")
            .status("2:dummy-app", unbalanced)
            .add()
            .healthConfig(HEALTH_CONFIG).build(),
        Cluster.builder().host("localhost").slot(SLOT_1)
            .stage().name("PROD").prefix("").suffix("").count(1).indexLength(0)
            .loadBalancerConfig("reload", "direct")
            .add()
            .healthConfig(HEALTH_CONFIG).build()
    };

    @SneakyThrows(URISyntaxException.class)
    public static List<Cluster> readClusterConfig() {
        return Clusters.readFrom(Paths.get(ClusterTest.class.getResource("test-cluster-config.yaml").toURI()));
    }


    @Test void shouldReadYamlConfig() {
        List<Cluster> clusters = readClusterConfig();

        assertThat(clusters).containsExactly(CLUSTERS);
    }

    @Test void shouldProduceAllUris() {
        Cluster cluster = CLUSTERS[0];
        Stream<URI> uris = cluster.stages().flatMap(stage -> stage.nodes(cluster)).map(ClusterNode::uri);
        assertThat(uris).containsExactly(
            URI.create("http://server-a" + "dev01.server.lan:8180"),
            URI.create("http://server-a" + "dev02.server.lan:8180"),
            URI.create("http://qa" + "server-a01.server.lan:8180"),
            URI.create("http://qa" + "server-a02.server.lan:8180"),
            URI.create("http://server-a01.server.lan:8180"),
            URI.create("http://server-a02.server.lan:8180"),
            URI.create("http://server-a03.server.lan:8180")
        );
    }
}
