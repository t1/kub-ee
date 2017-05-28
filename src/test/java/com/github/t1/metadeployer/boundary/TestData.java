package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.model.Deployment;
import com.github.t1.metadeployer.model.Deployment.DeploymentBuilder;

public class TestData {
    public static DeploymentBuilder unknownDeployment() {
        return Deployment.builder()
                         .groupId("unknown")
                         .artifactId("unknown")
                         .type("unknown")
                         .version("unknown");
    }
}
