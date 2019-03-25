package com.github.t1.kubee.gateway.deployer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.t1.kubee.model.Audits;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Deployment;
import com.github.t1.kubee.tools.http.YamlHttpClient;
import com.github.t1.log.Logged;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static com.github.t1.log.LogLevel.INFO;
import static java.util.stream.Collectors.toList;

@Logged(level = INFO)
@Slf4j
public class DeployerGateway {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<List<String>>() {};

    @Inject YamlHttpClient client;

    public String fetchVersion(String deployableName, ClusterNode node) {
        return fetchDeploymentsFrom(node.deployerUri())
            .stream()
            .filter(deployable -> deployable.getName().equals(deployableName))
            .findAny()
            .map(Deployable::getVersion)
            .orElse(null);
    }

    public List<String> fetchVersions(URI deployerUri, String groupId, String artifactId) {
        URI uri = UriBuilder.fromUri(deployerUri)
            .path("/repository/versions")
            .queryParam("artifactId", artifactId)
            .queryParam("groupId", groupId)
            .build();
        log.debug("GET versions from {}", uri);
        return client.GET(uri, STRING_LIST);
    }

    @Data
    private static class Deployables {
        @JsonProperty
        private Map<String, Deployable> deployables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    static class Deployable {
        @JsonProperty
        private String name;
        @JsonProperty("group-id")
        private String groupId;
        @JsonProperty("artifact-id")
        private String artifactId;
        @JsonProperty
        private String type;
        @JsonProperty
        private String version;
        @JsonProperty
        private String error;
    }

    public Stream<Deployment> fetchDeployablesFrom(ClusterNode node) {
        return fetchDeploymentsFrom(node.deployerUri())
            .stream()
            .map(deployable ->
                Deployment.builder()
                    .node(node)
                    .name(deployable.getName())
                    .groupId(orUnknown(deployable.getGroupId()))
                    .artifactId(orUnknown(deployable.getArtifactId()))
                    .version(orUnknown(deployable.getVersion()))
                    .type(orUnknown(deployable.getType()))
                    .error(deployable.getError())
                    .build());
    }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    List<Deployable> fetchDeploymentsFrom(URI uri) {
        log.debug("GET deployments from {}", uri);
        Deployables deployables = client.GET(uri, Deployables.class);
        return toDeployableList(deployables);
    }

    private List<Deployable> toDeployableList(Deployables deployables) {
        return deployables
            .getDeployables()
            .entrySet()
            .stream()
            .map(this::flatten)
            .collect(toList());
    }


    private Deployable flatten(Map.Entry<String, Deployable> entry) {
        Deployable deployable = entry.getValue();
        deployable.name = entry.getKey();
        return deployable;
    }


    public Audits deploy(ClusterNode node, String deploymentName, String version) {
        return postDeployer(node, deploymentName, "version", version);
    }

    public Audits undeploy(ClusterNode node, String deploymentName) {
        return postDeployer(node, deploymentName, "state", "undeployed");
    }

    private Audits postDeployer(ClusterNode node, String deploymentName, String key, String value) {
        URI uri = node.deployerUri();
        String parameterName = deploymentName + "." + key;
        log.debug("POST {}={} to {}", parameterName, value, uri);
        return client.POST(uri, new Form(parameterName, value), Audits::parseYaml);
    }
}
