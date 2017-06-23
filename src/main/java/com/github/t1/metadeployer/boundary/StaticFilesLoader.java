package com.github.t1.metadeployer.boundary;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.ws.rs.core.*;
import java.io.InputStream;

import static javax.ws.rs.core.MediaType.*;

@Slf4j
@RequiredArgsConstructor
public class StaticFilesLoader {
    protected final String name;
    private final String prefix;

    @SuppressWarnings({ "resource", "IOResourceOpenedButNotSafelyClosed" })
    public Response response(String filePath) {
        String path = prefix + filePath;
        log.debug("look for static file: {}: {}: {}", name, prefix, filePath);
        InputStream stream = classLoader().getResourceAsStream(path);
        if (stream == null)
            return null;
        log.debug("found {} in {}", filePath, name);
        return Response.ok(stream).type(type(fileSuffix(filePath))).build();
    }

    public static String fileSuffix(String filePath) {
        if (filePath == null)
            return null;
        int i = filePath.lastIndexOf('.');
        if (i < 0)
            return null;
        return filePath.substring(i);
    }

    public static MediaType type(String fileSuffix) {
        if (fileSuffix == null)
            return null;
        switch (fileSuffix) {
        case ".css":
            return valueOf("text/css");
        case ".html":
            return TEXT_HTML_TYPE;

        case ".gif":
            return valueOf("image/gif");
        case ".ico":
            return valueOf("image/x-icon");
        case ".jpeg":
            return valueOf("image/jpeg");
        case ".png":
            return valueOf("image/png");

        case ".raml":
            return valueOf("application/raml+yaml");

        default:
            return TEXT_PLAIN_TYPE;
        }
    }

    public static ClassLoader classLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null)
            loader = StaticFilesLoader.classLoader();
        return loader;
    }
}
