package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.gateway.DeployerGateway.Deployable;

public class TestData {
    public static Deployable emptyChecksumDeployable(String name) {
        return deployable().name(name).error("empty checksum").build();
    }

    public static Deployable.DeployableBuilder deployable() {
        return Deployable.builder()
                         .groupId("unknown")
                         .artifactId("unknown")
                         .type("unknown")
                         .version("unknown");
    }
}
