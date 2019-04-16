package com.github.t1.kubee.boundary.config;

import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Central producer for all configuration points.
 * <p>
 * Uses Microprofile-Config, e.g. in the Wildfly CLI you may once need to initialize the config properties like this:
 * <p><code>/subsystem=microprofile-config-smallrye/config-source=props:add(properties={})</code></p>
 * Then you can set values like this:
 * <p><code>/subsystem=microprofile-config-smallrye/config-source=props:map-put(name="properties",key="com.github.t1.kubee.dockerComposeDir",value=".../docker/"})</code></p>
 * And do a <code>reload</code> to activate the change(s).
 */
@SuppressWarnings("CdiInjectionPointsInspection")
class Config {
    static final String CONFIG_PROPERTY_PREFIX = "com.github.t1.kubee.";

    @Inject org.eclipse.microprofile.config.Config config;

    private Optional<Path> asPath(String name) {
        return config.getOptionalValue(CONFIG_PROPERTY_PREFIX + name, String.class).map(value -> Paths.get(value));
    }

    @Produces @DockerComposeDir Path dockerComposeDir() { return asPath("dockerComposeDir").orElse(null); }

    @Produces @ClusterConfigPath Path clusterConfigPath() { return asPath("clusterConfigPath").orElseGet(this::clusterConfigPathFallback);}

    private Path clusterConfigPathFallback() {
        String dir = System.getProperty("jboss.server.config.dir");
        if (dir == null)
            dir = System.getProperty("user.dir");
        return Paths.get(dir, "cluster-config.yaml");
    }
}
