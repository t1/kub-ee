package com.github.t1.kubee.gateway.deployer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.t1.kubee.model.*;
import com.github.t1.log.Logged;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.*;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Stream;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static com.github.t1.kubee.tools.ProblemDetail.*;
import static com.github.t1.log.LogLevel.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.*;
import static javax.ws.rs.core.Response.Status.Family.*;

@Logged(level = INFO)
@Slf4j
public class DeployerGateway {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            // .enable(MINIMIZE_QUOTES)
            .disable(WRITE_DOC_START_MARKER))
            .setSerializationInclusion(NON_EMPTY)
            // preferable to @JsonNaming, but conflicts in container: .setPropertyNamingStrategy(KEBAB_CASE)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

    private final Client httpClient = ClientBuilder.newClient();

    private String fetchYaml(URI uri) {
        Response response = httpClient
                .target(uri)
                .request()
                .accept(APPLICATION_YAML_TYPE)
                .get();
        try {
            String contentType = response.getHeaderString("Content-Type");
            String string = response.readEntity(String.class);
            if (NOT_FOUND.getStatusCode() == response.getStatus()) {
                log.info("{} returns 404 Not Found: {}", uri, string);
                throw new DeployerNotFoundException();
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

    public static class DeployerNotFoundException extends NotFoundException {
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
        return convertToVersionList(fetchYaml(uri));
    }

    @SneakyThrows(IOException.class)
    private List<String> convertToVersionList(String string) {
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

    List<Deployable> fetchDeploymentsFrom(URI uri) { return convertToDeployableList(fetchYaml(uri)); }

    private String orUnknown(String value) { return (value == null || value.isEmpty()) ? "unknown" : value; }

    @SneakyThrows(IOException.class)
    private List<Deployable> convertToDeployableList(String string) {
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


    public void deploy(URI uri, String deploymentName, String version) {
        postDeployer(uri, deploymentName, "version", version);
    }

    public void undeploy(URI uri, String deploymentName) {
        postDeployer(uri, deploymentName, "state", "undeployed");
    }

    private void postDeployer(URI uri, String deploymentName, String key, String value) {
        Response response = httpClient
                .target(uri)
                .request()
                .accept(APPLICATION_YAML_TYPE)
                .post(Entity.form(new Form(deploymentName + "." + key, value)));
        try {
            String contentType = response.getHeaderString("Content-Type");
            String string = response.readEntity(String.class);
            if (NOT_FOUND.getStatusCode() == response.getStatus()) {
                log.info("{} returns 404 Not Found: {}", uri, string);
                throw new NotFoundException();
            }
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw badGateway()
                        .detail("got " + statusInfo(response) + " from " + uri + ": " + string)
                        .exception();
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw badGateway()
                        .detail("expected " + APPLICATION_YAML_TYPE + " but got " + contentType + ": " + string)
                        .exception();
        } finally {
            response.close();
        }
    }
}
