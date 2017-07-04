package com.github.t1.metadeployer.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.t1.log.Logged;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.ejb.Asynchronous;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static com.github.t1.log.LogLevel.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.Family.*;
import static javax.ws.rs.core.Response.Status.*;

@Logged(level = INFO)
@Slf4j
public class DeployerGateway {
    public static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            // .enable(MINIMIZE_QUOTES)
            .disable(WRITE_DOC_START_MARKER))
            .setSerializationInclusion(NON_EMPTY)
            // preferable to @JsonNaming, but conflicts in container: .setPropertyNamingStrategy(KEBAB_CASE)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

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
    public static class Deployables {
        @JsonProperty
        private Map<String, Deployable> deployables;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Deployable {
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

    private final Client httpClient = ClientBuilder.newClient();

    public List<Deployable> fetchDeployablesOn(URI uri) { return convertToDeployableList(fetchYaml(uri)); }

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
                throw new NotFoundException();
            }
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw new RuntimeException("got " + statusInfo(response) + " from " + uri + ": " + string);
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw new RuntimeException("expected " + APPLICATION_YAML_TYPE
                        + " but got " + contentType + ": " + string);
            return string;
        } finally {
            response.close();
        }
    }

    @SneakyThrows(IOException.class)
    private List<Deployable> convertToDeployableList(String string) {
        return YAML.readValue(string, Deployables.class)
                   .getDeployables()
                   .entrySet()
                   .stream()
                   .map(this::flatten)
                   .collect(toList());
    }

    private String statusInfo(Response response) { return response.getStatus() + " " + response.getStatusInfo(); }

    private Deployable flatten(Map.Entry<String, Deployable> entry) {
        Deployable deployable = entry.getValue();
        deployable.name = entry.getKey();
        return deployable;
    }


    @Asynchronous
    public void startVersionDeploy(URI uri, String application, String version) {
        log.info("deploy {} {} on {}", application, version, uri);
        Response response = httpClient
                .target(uri)
                .request()
                .accept(APPLICATION_YAML_TYPE)
                .post(Entity.form(new Form(application + ".version", version)));
        try {
            String contentType = response.getHeaderString("Content-Type");
            String string = response.readEntity(String.class);
            if (NOT_FOUND.getStatusCode() == response.getStatus()) {
                log.info("{} returns 404 Not Found: {}", uri, string);
                throw new NotFoundException();
            }
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw new RuntimeException("got " + statusInfo(response) + " from " + uri + ": " + string);
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw new RuntimeException("expected " + APPLICATION_YAML_TYPE
                        + " but got " + contentType + ": " + string);
        } finally {
            response.close();
        }
    }
}
