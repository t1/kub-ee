package com.github.t1.kubee;

import com.github.t1.kubee.boundary.gateway.ingress.ReloadMock;
import com.github.t1.kubee.entity.Cluster;
import com.github.t1.kubee.entity.ClusterNode;
import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.entity.Stage;
import com.github.t1.nginx.HostPort;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestData {
    public static final HostPort WORKER01 = HostPort.valueOf("worker-prod1:10001");
    public static final HostPort WORKER02 = HostPort.valueOf("worker-prod2:10002");
    public static final String PROXY_SETTINGS = "proxy_set_header Host      $host;\n" +
        "            proxy_set_header X-Real-IP $remote_addr;";

    public static final Stage STAGE = Stage.builder().name("PROD").suffix("-prod").count(2)
        .loadBalancerConfig("reload", "custom")
        .loadBalancerConfig("class", ReloadMock.class.getName())
        .build();
    public static final Slot SLOT = Slot.builder().name("0").http(8080).build();
    public static final Cluster CLUSTER = Cluster.builder().host("worker").slot(SLOT).stage(STAGE).build();
    public static final ClusterNode NODE1 = new ClusterNode(CLUSTER, STAGE, 1);
    public static final ClusterNode NODE2 = new ClusterNode(CLUSTER, STAGE, 2);
}
