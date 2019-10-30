package com.github.t1.kubee;

import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Cluster.HealthConfig;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.kubee.entity.Version;
import com.github.t1.nginx.HostPort;
import lombok.experimental.UtilityClass;

import java.util.List;

import static com.github.t1.kubee.entity.DeploymentStatus.unbalanced;
import static com.github.t1.kubee.entity.VersionStatus.deployed;
import static com.github.t1.kubee.entity.VersionStatus.undeployed;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

@UtilityClass
public class TestData {
    public static final HostPort WORKER01 = HostPort.valueOf("worker-prod1:10001");
    public static final HostPort WORKER02 = HostPort.valueOf("worker-prod2:10002");
    public static final String PROXY_SETTINGS = "proxy_set_header Host      $host;\n" +
        "            proxy_set_header X-Real-IP $remote_addr;";

    public static final Stage STAGE = Stage.builder().name("PROD").provider("docker-compose").suffix("-prod").count(2)
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();
    public static final Slot SLOT_0 = Slot.builder().name("0").http(8080).https(8443).build();
    public static final Slot SLOT_1 = Slot.builder().name("1").http(8180).https(8543).build();
    public static final Slot SLOT_2 = Slot.builder().name("2").http(8280).https(8643).build();
    public static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT_0).stage(STAGE).build();
    public static final ClusterNode NODE1 = STAGE.nodeAt(CLUSTER, 1);
    public static final ClusterNode NODE2 = STAGE.nodeAt(CLUSTER, 2);

    public static final String APPLICATION_NAME = "dummy-app";
    public static final String VERSION_100 = "1.0.0";
    public static final String VERSION_101 = "1.0.1";
    public static final String VERSION_102 = "1.0.2";
    public static final String VERSION_103 = "1.0.3";
    public static final String DEPLOYED_VERSION = VERSION_101;
    public static final List<String> VERSIONS = asList(VERSION_100, VERSION_101, VERSION_102, VERSION_103);
    public static final List<Version> VERSIONS_STATUS = VERSIONS.stream()
        .map(version -> new Version(version, (version.equals(DEPLOYED_VERSION)) ? deployed : undeployed))
        .collect(toList());
    public static final Deployment DEPLOYMENT = Deployment.builder()
        .name(APPLICATION_NAME)
        .node(NODE1)
        .groupId("app-group")
        .artifactId("app-artifact")
        .version(DEPLOYED_VERSION)
        .build();

    private static final HealthConfig HEALTH_CONFIG = HealthConfig.builder().path("dummy/health/path").build();

    public static final Stage DEV = Stage.builder().name("DEV").prefix("").suffix("dev").count(2).indexLength(2)
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();
    private static final Stage QA = Stage.builder().name("QA").prefix("qa").suffix("").count(2).indexLength(2)
        .loadBalancerConfig("reload", "service")
        .loadBalancerConfig("port", "12345")
        .build();
    public static final Stage PROD = Stage.builder().name("PROD").prefix("").suffix("").count(3).indexLength(2)
        .loadBalancerConfig("reload", "direct").build();

    public static final Cluster CLUSTER_A1 = Cluster.builder().host("server-a.server.lan").slot(SLOT_1)
        .stage(DEV)
        .stage(QA)
        .stage(PROD)
        .healthConfig(HEALTH_CONFIG)
        .build();
    public static final Cluster CLUSTER_A2 = Cluster.builder().host("server-a.server.lan").slot(SLOT_2)
        .stage(DEV)
        .stage(QA)
        .stage(PROD)
        .healthConfig(HEALTH_CONFIG).build();
    public static final Cluster CLUSTER_B2 = Cluster.builder().host("server-b.server.lan").slot(SLOT_2)
        .stage().name("DEV").prefix("").suffix("test").count(2).indexLength(2)
        .loadBalancerConfig("reload", "set-user-id-script")
        .add()
        .stage(QA)
        .stage().name("PROD").count(5).indexLength(2)
        .loadBalancerConfig("reload", "docker-kill-hup")
        .status("2:dummy-app", unbalanced)
        .add()
        .healthConfig(HEALTH_CONFIG)
        .build();
    public static final Cluster CLUSTER_LOCALHOST = Cluster.builder().host("localhost").slot(SLOT_1)
        .stage().name("PROD").prefix("").suffix("").count(1).indexLength(0)
        .loadBalancerConfig("reload", "direct")
        .add()
        .healthConfig(HEALTH_CONFIG)
        .build();

    public static final Cluster[] CLUSTERS = {
        CLUSTER_A1,
        CLUSTER_A2,
        CLUSTER_B2,
        CLUSTER_LOCALHOST
    };

    public static final ClusterNode UNBALANCED_NODE = CLUSTER_B2.node("PROD", 2);
}
