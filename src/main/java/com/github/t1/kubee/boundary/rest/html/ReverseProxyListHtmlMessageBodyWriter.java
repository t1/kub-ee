package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.entity.ReverseProxy;
import com.github.t1.kubee.tools.html.Html;
import com.github.t1.kubee.tools.html.Panel;
import com.github.t1.kubee.tools.html.Table;
import com.github.t1.kubee.tools.html.Table.TableRow;

import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
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
public class ReverseProxyListHtmlMessageBodyWriter implements MessageBodyWriter<List<ReverseProxy>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
            && ((ParameterizedType) genericType).getRawType().equals(List.class)
            && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(ReverseProxy.class);
    }

    @Override
    public long getSize(List<ReverseProxy> reverseProxies, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<ReverseProxy> reverseProxies, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(new ReverseProxiesHtml(reverseProxies).toString());
        out.flush();
    }

    private class ReverseProxiesHtml extends Html {
        private Table table;

        private ReverseProxiesHtml(List<ReverseProxy> reverseProxies) {
            header("Reverse Proxies");
            container().with(new Panel().heading("Reverse Proxies").with(table = new Table()));
            tableHeader();
            reverseProxies.forEach(this::row);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th("From");
            row.th("To");
        }

        private void row(ReverseProxy reverseProxy) {
            TableRow row = table.tr();
            row.td(reverseProxy.getFrom().toString());
            int to = reverseProxy.getTo();
            row.td((to < 0) ? "-" : Integer.toString(to));
        }
    }
}
