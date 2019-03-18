package com.github.t1.kubee.gateway.deployer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.t1.kubee.model.Audits;
import com.github.t1.kubee.model.ClusterNode;
import com.github.t1.kubee.model.Deployment;
import com.github.t1.log.Logged;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.github.t1.kubee.tools.http.ProblemDetail.badGateway;
import static com.github.t1.log.LogLevel.INFO;
import static java.util.stream.Collectors.toList;
import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

@Logged(level = INFO)
@Slf4j
public class DeployerGateway {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

    private final Client httpClient = ClientBuilder.newClient();

    private String fetchYaml(URI uri) {
        return fetchYaml(uri, Invocation.Builder::get);
    }

    private String fetchYaml(URI uri, Function<Invocation.Builder, Response> method) {
        log.debug("fetch yaml from {}", uri);
        Invocation.Builder invocation = httpClient
                .target(uri)
                .request()
                .accept(APPLICATION_YAML_TYPE);
        Response response = method.apply(invocation);
        try {
            String contentType = response.getHeaderString("Content-Type");
            String string = response.readEntity(String.class);
            if (NOT_FOUND.getStatusCode() == response.getStatus()) {
                log.info("{} returns 404 Not Found: {}", uri, string);
                throw new NotFoundException();
            }
            if (BAD_GATEWAY.getStatusCode() == response.getStatus()) {
                log.info("{} returns 502 Bad Gateway: {}", uri, string);
                throw new BadDeployerGatewayException(string);
            }
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw badGateway().detail("got " + statusInfo(response) + " from " + uri + ": " + string).exception();
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw badGateway()
                        .detail("expected " + APPLICATION_YAML_TYPE + " but got " + contentType + ": " + string)
                        .exception();
            return string;
        } finally {
            response.close();
        }
    }

    public String fetchVersion(String deployableName, ClusterNode node) {
        return fetchDeploymentsFrom(node.deployerUri())
                .stream()
                .filter(deployable -> deployable.getName().equals(deployableName))
                .findAny()
                .map(Deployable::getVersion)
                .orElse(null);
    }

    public static class BadDeployerGatewayException extends ServerErrorException {
        private BadDeployerGatewayException(String message) { super(message, BAD_GATEWAY); }
    }

    private String statusInfo(Response response) { return response.getStatus() + " " + response.getStatusInfo(); }


    public List<String> fetchVersions(URI deployerUri, String groupId, String artifactId) {
        URI uri = UriBuilder.fromUri(deployerUri)
                            .path("/repository/versions")
                            .queryParam("artifactId", artifactId)
                            .queryParam("groupId", groupId)
                            .build();
        return toVersionList(fetchYaml(uri));
    }

    @SneakyThrows(IOException.class)
    private List<String> toVersionList(String string) {
        return YAML.readValue(string, new TypeReference<List<String>>() {});
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

    List<Deployable> fetchDeploymentsFrom(URI uri) { return toDeployableList(fetchYaml(uri)); }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    @SneakyThrows(IOException.class)
    private List<Deployable> toDeployableList(String string) {
        return YAML.readValue(string, Deployables.class)
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
        return Audits.parseYaml(fetchYaml(node.deployerUri(),
                i -> i.post(Entity.form(new Form(deploymentName + "." + key, value)))));
    }
}
