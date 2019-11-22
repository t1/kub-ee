package com.github.t1.kubee;

import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.Cluster.HealthConfig;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Deployment;
import com.github.t1.kubee.entity.Endpoint;
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
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

@UtilityClass
public class TestData {
    public static final HostPort WORKER01 = HostPort.valueOf("worker01:10001");
    public static final HostPort WORKER02 = HostPort.valueOf("worker02:10002");
    public static final String PROXY_SETTINGS = "proxy_set_header Host      $host;\n" +
        "            proxy_set_header X-Real-IP $remote_addr;";

    public static final Stage LOCAL = Stage.builder().name("LOCAL").prefix("local-").count(1).build();

    public static final Stage DEV = Stage.builder().name("DEV").prefix("").suffix("dev").count(1).indexLength(0)
        .loadBalancerConfig("reload", "direct")
        .build();
    public static final Stage QA = Stage.builder().name("QA").prefix("qa-").suffix("").count(2).indexLength(1)
        .loadBalancerConfig("reload", "service")
        .loadBalancerConfig("port", "12345")
        .build();
    public static final Stage PROD = Stage.builder().name("PROD").prefix("").suffix("").count(3).indexLength(2)
        .provider("docker-compose")
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();

    public static final Slot SLOT_0 = Slot.named("0").withOffset(8000);
    public static final Slot SLOT_1 = Slot.named("1").withOffset(8100);
    public static final Slot SLOT_2 = Slot.named("2").withOffset(8200);

    public static final Cluster CLUSTER = Cluster.builder().host("worker")
        .slot(SLOT_0)
        .stage(LOCAL).stage(DEV).stage(QA).stage(PROD)
        .build();
    public static final Stage[] ALL_STAGES = CLUSTER.getStages().toArray(new Stage[0]);

    public static final ClusterNode PROD01 = PROD.nodeAt(CLUSTER, 1);
    public static final ClusterNode PROD02 = PROD.nodeAt(CLUSTER, 2);

    public static final Endpoint LOCAL_ENDPOINT = new Endpoint("local-worker", 8080);
    public static final Endpoint QA_ENDPOINT1 = new Endpoint("qa-worker1", 8080);
    public static final Endpoint QA_ENDPOINT2 = new Endpoint("qa-worker2", 8080);
    public static final Endpoint PROD_ENDPOINT1 = new Endpoint("worker01", 8080);
    public static final Endpoint PROD_ENDPOINT2 = new Endpoint("worker02", 8080);
    public static final Endpoint PROD_ENDPOINT3 = new Endpoint("worker03", 8080);
    public static final Endpoint PROD_ENDPOINT4 = new Endpoint("worker04", 8080);
    public static final Endpoint PROD_ENDPOINT5 = new Endpoint("worker05", 8080);
    public static final List<Endpoint> LOCAL_ENDPOINTS = singletonList(LOCAL_ENDPOINT);
    public static final List<Endpoint> QA_ENDPOINTS = asList(QA_ENDPOINT1, QA_ENDPOINT2);
    public static final List<Endpoint> PROD_ENDPOINTS = asList(PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3, PROD_ENDPOINT4, PROD_ENDPOINT5);
    public static final List<Endpoint> ALL_ENDPOINTS = asList(LOCAL_ENDPOINT, QA_ENDPOINT1, QA_ENDPOINT2,
        PROD_ENDPOINT1, PROD_ENDPOINT2, PROD_ENDPOINT3);

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
        .node(PROD01)
        .groupId("app-group")
        .artifactId("app-artifact")
        .version(DEPLOYED_VERSION)
        .build();

    private static final HealthConfig HEALTH_CONFIG = HealthConfig.builder().path("dummy/health/path").build();

    public static final Cluster CLUSTER_A1 = Cluster.builder().host("server-a.server.lan").slot(SLOT_1)
        .stage(DEV).stage(QA).stage(PROD)
        .healthConfig(HEALTH_CONFIG)
        .build();
    public static final Cluster CLUSTER_A2 = Cluster.builder().host("server-a.server.lan").slot(SLOT_2)
        .stage(DEV).stage(QA).stage(PROD)
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

    public static final Cluster[] ALL_CLUSTERS = {
        CLUSTER_A1,
        CLUSTER_A2,
        CLUSTER_B2,
        CLUSTER_LOCALHOST
    };
}
