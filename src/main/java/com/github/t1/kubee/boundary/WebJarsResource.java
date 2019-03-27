package com.github.t1.kubee.boundary;

import com.github.t1.log.Logged;
import lombok.extern.slf4j.Slf4j;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static com.github.t1.kubee.boundary.StaticFilesLoader.classLoader;
import static com.github.t1.log.LogLevel.TRACE;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;

/**
 * Serve [Webjars](http://www.webjars.org). This class is not suitable for heavy load. Really heavy load should be
 * fulfilled by serving static resources from e.g. Apache or even a CDN.
 * <p>
 * But the performance of this class could also be improved by letting the clients cache, by adding etags
 * (e.g. the version number), modified-since (from the pom.properties comment), and cache control headers.
 */
@Slf4j
@Path("/")
@ApplicationScoped
@Logged(level = TRACE)
public class WebJarsResource {
    private class NotFoundLoader extends StaticFilesLoader {
        public NotFoundLoader(String name) {
            super(name, "");
        }

        @Override
        public Response response(String filePath) {
            return notFound("no webjar found for " + name + ".");
        }
    }

    private static class WebjarFilesLoader extends StaticFilesLoader {
        public WebjarFilesLoader(String artifact, String version) {
            super(artifact + " webjar", "META-INF/resources/webjars/" + artifact + "/" + version + "/");
        }
    }

    private final Map<String, StaticFilesLoader> loaders = new HashMap<>();

    @GET
    @Path("/{artifact}/{filePath:.*}")
    public Response getStaticResource(@PathParam("artifact") String artifact, @PathParam("filePath") String filePath) {
        log.debug("getStaticResource({}, {})", artifact, filePath);
        StaticFilesLoader loader = getLoaderFor(artifact);
        if (loader == null)
            return notFound("artifact not found '" + artifact + "' (for path '" + filePath + "')");
        log.debug("serve {} from {}", filePath, loader.name);
        Response response = loader.response(filePath);
        if (response == null)
            return notFound("resource '" + filePath + "' not found in '" + artifact + "'");
        else
            return response;
    }

    private Response notFound(String message) {
        log.warn("not found: {}", message);
        return Response.status(NOT_FOUND).entity(message + "\n").type(TEXT_PLAIN).build();
    }

    private StaticFilesLoader getLoaderFor(String artifact) {
        return loaders.computeIfAbsent(artifact, this::createLoaderFor);
    }

    private StaticFilesLoader createLoaderFor(String artifact) {
        String version = versionOf(artifact);
        if (version == null)
            return new NotFoundLoader(artifact);
        return new WebjarFilesLoader(artifact, version);
    }

    private String versionOf(String artifact) {
        URL resource = pomPropertiesResourceFor(artifact);
        if (resource == null) {
            log.debug("no pom properties found for {}", artifact);
            return null;
        }
        try (InputStream is = resource.openStream()) {
            Properties properties = new Properties();
            properties.load(is);
            assert properties.getProperty("artifactId").equals(artifact);
            String version = properties.getProperty("version");
            log.debug("found version {} for {}", version, artifact);
            return version;
        } catch (IOException e) {
            log.debug("exception while loading {}: {}", artifact, e);
            return null;
        }
    }

    private URL pomPropertiesResourceFor(String artifact) {
        String path = "/META-INF/maven/org.webjars/" + artifact + "/pom.properties";
        URL resource = classLoader().getResource(path);
        if (resource == null) {
            path = "/META-INF/maven/org.webjars.npm/" + artifact + "/pom.properties";
            resource = classLoader().getResource(path);
        }
        if (resource == null) {
            path = "/META-INF/maven/org.webjars.bower/" + artifact + "/pom.properties";
            resource = classLoader().getResource(path);
        }
        return resource;
    }
}
