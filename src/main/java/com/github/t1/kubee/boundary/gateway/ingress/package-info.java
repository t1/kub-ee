/**
 * See {@link com.github.t1.kubee.boundary.gateway.ingress.Ingress}.
 *
 * NOTE: This package is free of any library dependencies, so it can be used easily from the CLI boundary, too.
 */
@DependsUpon(packagesOf = {
    com.github.t1.kubee.boundary.cli.reload.NginxReloadService.class,
    com.github.t1.kubee.entity.LoadBalancer.class,
    com.github.t1.kubee.tools.Tools.class,

    com.github.t1.nginx.NginxConfig.class,
})
package com.github.t1.kubee.boundary.gateway.ingress;

import com.github.t1.testtools.DependsUpon;
