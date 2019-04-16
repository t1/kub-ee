package com.github.t1.kubee.boundary.config;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * The directory containing the <code>docker-compose.yaml</code> file
 * that is used to setup the docker containers for the workers.
 * Only required when you call the <code>reconfigure</code> endpoint.
 */
@Qualifier
@Retention(RUNTIME)
public @interface DockerComposeDir {}
