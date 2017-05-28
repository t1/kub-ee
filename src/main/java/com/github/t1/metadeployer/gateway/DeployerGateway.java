package com.github.t1.metadeployer.gateway;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URI;
import java.util.*;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.*;
import static com.fasterxml.jackson.dataformat.yaml.YAMLGenerator.Feature.*;
import static java.util.stream.Collectors.*;
import static javax.ws.rs.core.Response.Status.Family.*;
import static javax.ws.rs.core.Response.Status.*;

@Slf4j
public class DeployerGateway {
    public static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory()
            // .enable(MINIMIZE_QUOTES)
            .disable(WRITE_DOC_START_MARKER))
            .setSerializationInclusion(NON_EMPTY)
            // preferable to @JsonNaming, but conflicts in container: .setPropertyNamingStrategy(KEBAB_CASE)
            .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
            .findAndRegisterModules();

    // private static final int CONNECT_TIMEOUT = 10_000;
    // private static final int CONNECTION_REQUEST_TIMEOUT = CONNECT_TIMEOUT;
    // private static final int SOCKET_TIMEOUT = CONNECT_TIMEOUT;
    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

    @Data
    public static class Plan {
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

    public List<Deployable> fetchDeployablesOn(URI uri) {
        uri = UriBuilder.fromUri(uri).path("/deployer").build();
        return convert(uri, from(uri));
    }

    private Response from(URI uri) {
        return httpClient
                .target(uri)
                .request()
                .accept(APPLICATION_YAML_TYPE)
                .buildGet()
                .invoke();
    }

    @SneakyThrows(IOException.class)
    private List<Deployable> convert(URI uri, Response response) {
        try {
            String contentType = response.getHeaderString("Content-Type");
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw new RuntimeException("expected " + APPLICATION_YAML_TYPE + " but got " + contentType);
            String string = response.readEntity(String.class);
            if (response.getStatusInfo() == NOT_FOUND)
                throw new DeployerNotFoundException();
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw new RuntimeException(response.getStatusInfo() + " on " + uri);
            return YAML.readValue(string, Plan.class)
                       .getDeployables()
                       .entrySet()
                       .stream()
                       .map(this::flatten)
                       .collect(toList());
        } finally {
            response.close();
        }
    }

    private Deployable flatten(Map.Entry<String, Deployable> entry) {
        Deployable deployable = entry.getValue();
        deployable.name = entry.getKey();
        return deployable;
    }

    public static class DeployerNotFoundException extends RuntimeException {}
}
