package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.tools.html.A;
import com.github.t1.kubee.tools.html.Html;
import com.github.t1.kubee.tools.html.HtmlList;
import com.github.t1.kubee.tools.html.PageHeader;

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
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static javax.ws.rs.core.MediaType.TEXT_HTML;

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
            list.withItem(new A(link));
        }
    }
}
