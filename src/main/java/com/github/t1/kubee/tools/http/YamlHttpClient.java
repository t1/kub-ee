package com.github.t1.kubee.tools.http;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

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
import java.net.URI;
import java.util.function.Function;

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
    private static final Yaml YAML;

    static {
        Constructor constructor = new Constructor();
        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);
        YAML = new Yaml(constructor);
    }

    private static final MediaType APPLICATION_YAML_TYPE = MediaType.valueOf("application/yaml");

    private final Client httpClient = ClientBuilder.newClient();

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
        try (Response response = method.apply(invocation)) {
            String contentType = response.getHeaderString("Content-Type");
            String body = response.readEntity(String.class).replace("\r\n", "\n");
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
        }
    }

    private <T> T parse(String string, Class<T> type) { return YAML.loadAs(string, type); }

    private String statusInfo(Response response) { return response.getStatus() + " " + response.getStatusInfo(); }

    public static class BadGatewayException extends ServerErrorException {
        private BadGatewayException(String message) { super(message, BAD_GATEWAY); }
    }
}
