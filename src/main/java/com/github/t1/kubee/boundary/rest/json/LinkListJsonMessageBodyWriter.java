package com.github.t1.kubee.boundary.rest.json;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static java.util.stream.Collectors.joining;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;

@Provider
@Produces(APPLICATION_JSON)
public class LinkListJsonMessageBodyWriter implements MessageBodyWriter<List<Link>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
            && ((ParameterizedType) genericType).getRawType().equals(List.class)
            && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Link.class);
    }

    @Override
    public long getSize(List<Link> links, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Link> links, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(links.stream()
            .map(this::toJson)
            .collect(joining(",", "{", "}")));
        out.flush();
    }

    private String toJson(Link link) {
        StringWriter out = new StringWriter();
        out.write("\"" + link.getRel() + "\":{");
        out.write("\"href\":\"" + link.getUri() + "\"");
        if (link.getTitle() != null)
            out.write(",\"title\":\"" + link.getTitle() + "\"");
        if (link.getType() != null)
            out.write(",\"type\":\"" + link.getType() + "\"");
        out.write("}");
        return out.toString();
    }
}
