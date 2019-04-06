@DependsUpon(packagesOf = {
    com.github.t1.kubee.boundary.cli.reload.NginxReloadService.class,
    com.github.t1.kubee.entity.LoadBalancer.class,
    com.github.t1.kubee.tools.Tools.class,
    com.github.t1.kubee.tools.http.ProblemDetail.class,

    com.github.t1.nginx.NginxConfig.class,
})
package com.github.t1.kubee.boundary.gateway.loadbalancer;

import com.github.t1.testtools.DependsUpon;
