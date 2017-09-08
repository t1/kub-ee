@DependsUpon(packagesOf = {
        com.github.t1.kubee.gateway.deployer.DeployerGateway.class,
        com.github.t1.kubee.gateway.health.HealthGateway.class,
        com.github.t1.kubee.gateway.loadbalancer.LoadBalancerGateway.class,
        com.github.t1.kubee.model.Cluster.class,
        com.github.t1.kubee.tools.http.ProblemDetail.class,
        com.github.t1.kubee.tools.yaml.YamlDocument.class,
})
package com.github.t1.kubee.control;

import com.github.t1.testtools.DependsUpon;
