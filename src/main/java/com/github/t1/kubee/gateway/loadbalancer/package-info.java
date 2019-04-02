@DependsUpon(packagesOf = {
    com.github.t1.kubee.gateway.loadbalancer.tools.lb.NginxReloadService.class,
    com.github.t1.kubee.model.LoadBalancer.class,
    com.github.t1.kubee.tools.http.ProblemDetail.class,

    com.github.t1.nginx.NginxConfig.class,
})
package com.github.t1.kubee.gateway.loadbalancer;

import com.github.t1.testtools.DependsUpon;
