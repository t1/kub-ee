package com.github.t1.kubee.tools.http;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Form;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.URI;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.github.t1.kubee.tools.http.ProblemDetail.badGateway;
import static javax.ws.rs.core.Response.Status.BAD_GATEWAY;
import static javax.ws.rs.core.Response.Status.Family.SUCCESSFUL;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Wraps a JAX-RS {@link Client} with requesting and parsing the response as YAML.
 */
@SuppressWarnings({"SameParameterValue", "WeakerAccess"})
@Slf4j
public class YamlHttpClient {
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
        .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
        .findAndRegisterModules();

    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

    private final Client httpClient = ClientBuilder.newClient();

    public <T> T GET(URI uri, TypeReference<T> type) { return GET(uri, yaml -> parse(yaml, type)); }

    public <T> T GET(URI uri, Class<T> type) { return GET(uri, yaml -> parse(yaml, type)); }

    public <T> T GET(URI uri, Function<String, T> responseParser) {
        String responseYaml = requestYaml(uri, Builder::get);
        return responseParser.apply(responseYaml);
    }


    public <T> T POST(URI uri, Form form, Function<String, T> responseParser) {
        String responseYaml = requestYaml(uri, i -> i.post(Entity.form(form)));
        return responseParser.apply(responseYaml);
    }

    private String requestYaml(URI uri, Function<Builder, Response> method) {
        Invocation.Builder invocation = httpClient
            .target(uri)
            .request()
            .accept(APPLICATION_YAML_TYPE);
        Response response = method.apply(invocation);
        try {
            String contentType = response.getHeaderString("Content-Type");
            String body = response.readEntity(String.class);
            if (NOT_FOUND.getStatusCode() == response.getStatus()) {
                log.info("{} returns 404 Not Found: {}", uri, body);
                throw new NotFoundException();
            }
            if (BAD_GATEWAY.getStatusCode() == response.getStatus()) {
                log.info("{} returns 502 Bad Gateway: {}", uri, body);
                throw new BadGatewayException(body);
            }
            if (response.getStatusInfo().getFamily() != SUCCESSFUL)
                throw badGateway().detail("got " + statusInfo(response) + " from " + uri + ": " + body).exception();
            if (!APPLICATION_YAML_TYPE.toString().equals(contentType))
                throw badGateway().detail("expected " + APPLICATION_YAML_TYPE + " but got " + contentType + ": " + body).exception();
            return body;
        } finally {
            response.close();
        }
    }

    @SneakyThrows(IOException.class) private <T> T parse(String string, TypeReference<T> type) { return YAML.readValue(string, type); }

    @SneakyThrows(IOException.class) private <T> T parse(String string, Class<T> type) { return YAML.readValue(string, type); }

    private String statusInfo(Response response) { return response.getStatus() + " " + response.getStatusInfo(); }

    public static class BadGatewayException extends ServerErrorException {
        private BadGatewayException(String message) { super(message, BAD_GATEWAY); }
    }
}
