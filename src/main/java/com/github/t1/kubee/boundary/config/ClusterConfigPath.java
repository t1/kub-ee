package com.github.t1.kubee.boundary.config;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The <code>cluster-config.yaml</code> file that defines the clusters that <code>kub-ee</code> manages.
 *
 * @see com.github.t1.kubee.entity.Cluster for details
 */
@Qualifier
@Retention(RUNTIME)
public @interface ClusterConfigPath {}
