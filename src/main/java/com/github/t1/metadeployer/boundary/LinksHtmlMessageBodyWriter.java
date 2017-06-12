package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.tools.html.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.URI;
import java.util.Map;

import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class LinksHtmlMessageBodyWriter implements MessageBodyWriter<Map<String, URI>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
                && ((ParameterizedType) genericType).getRawType().equals(Map.class)
                && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(String.class)
                && ((ParameterizedType) genericType).getActualTypeArguments()[1].equals(URI.class);
    }

    @Override
    public long getSize(Map<String, URI> links, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(Map<String, URI> links, Class<?> type, Type genericType,
            Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(new LinksHtml(links).toString());
        out.flush();
    }

    private class LinksHtml extends Html {
        private final Map<String, URI> links;
        private List list;

        private LinksHtml(Map<String, URI> links) {
            this.links = links;

            header("Index");
            h1("Links");
            this.list = ul();
            this.links.forEach(this::link);
        }

        private void link(String name, URI href) {
            list.li().appendElement("a").attr("href", href.toString()).text(name);
        }
    }
}
