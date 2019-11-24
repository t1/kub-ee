@DependsUpon(packagesOf = {
    com.github.t1.kubee.boundary.gateway.clusters.ClusterStore.class,
    com.github.t1.kubee.boundary.gateway.container.ClusterStatus.class,
    com.github.t1.kubee.boundary.gateway.deployer.DeployerGateway.class,
    com.github.t1.kubee.boundary.gateway.health.HealthGateway.class,
    com.github.t1.kubee.boundary.gateway.ingress.Ingress.class,
    com.github.t1.kubee.entity.Cluster.class,
    com.github.t1.kubee.tools.http.ProblemDetail.class,
})
package com.github.t1.kubee.control;

import com.github.t1.testtools.DependsUpon;
