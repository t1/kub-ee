package com.github.t1.kubee.boundary.html;

import com.github.t1.kubee.tools.html.*;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.ws.rs.ext.*;
import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.List;

import static javax.ws.rs.core.MediaType.*;

@Provider
@Produces(TEXT_HTML)
public class LinkListHtmlMessageBodyWriter implements MessageBodyWriter<List<Link>> {
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
        out.write(new LinksHtml(links).toString());
        out.flush();
    }

    private class LinksHtml extends Html {
        private final List<Link> links;
        private HtmlList list;

        private LinksHtml(List<Link> links) {
            baseUri("api/");
            this.links = links;

            header("Index");
            container()
                    .with(new PageHeader("Links"))
                    .with(this.list = new HtmlList());
            this.links.forEach(this::link);
        }

        private void link(Link link) {
            // list-group-items with an `a` look nicer (clickable) than a list of `a`s.
            list.li().appendElement("a")
                .attr("rel", link.getRel())
                .attr("href", link.getUri().toString())
                .text(link.getTitle());
        }
    }
}
