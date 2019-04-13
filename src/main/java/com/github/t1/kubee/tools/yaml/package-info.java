/**
 * Nicer API for <a href="https://bitbucket.org/asomov/snakeyaml">snake-yaml</a>
 *
 * @see com.github.t1.kubee.tools.yaml.YamlDocument YamlDocument as entry point
 */
@DependsUpon(packagesOf = {
    org.yaml.snakeyaml.Yaml.class,
    org.yaml.snakeyaml.emitter.Emitter.class,
    org.yaml.snakeyaml.error.YAMLException.class,
    org.yaml.snakeyaml.resolver.Resolver.class,
    org.yaml.snakeyaml.serializer.Serializer.class,
    org.yaml.snakeyaml.nodes.MappingNode.class,
})
package com.github.t1.kubee.tools.yaml;

import com.github.t1.testtools.DependsUpon;
