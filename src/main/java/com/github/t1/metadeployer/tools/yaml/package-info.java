/**
 * Nicer API for <a href="https://bitbucket.org/asomov/snakeyaml">snake-yaml</a>
 *
 * @see com.github.t1.metadeployer.tools.yaml.YamlDocument YamlDocument as entry point
 */
@DependsUpon(packagesOf = {
        org.yaml.snakeyaml.Yaml.class,
        org.yaml.snakeyaml.nodes.MappingNode.class,
})
package com.github.t1.metadeployer.tools.yaml;

import com.github.t1.testtools.DependsUpon;
