package com.github.t1.metadeployer.boundary.html;

import com.github.t1.metadeployer.model.ReverseProxy;
import com.github.t1.metadeployer.tools.html.*;
import com.github.t1.metadeployer.tools.html.Table.TableRow;

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
        private final List<ReverseProxy> reverseProxies;
        private Table table;

        private ReverseProxiesHtml(List<ReverseProxy> reverseProxies) {
            this.reverseProxies = reverseProxies;

            header("Reverse Proxies");
            container().with(new Panel().heading("Reverse Proxies").with(table = new Table()));
            tableHeader();
            this.reverseProxies.forEach(this::row);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th().text("from");
            row.th().text("to");
        }

        private void row(ReverseProxy reverseProxy) {
            TableRow row = table.tr();
            row.td().text(reverseProxy.getFrom().toString());
            row.td().text(reverseProxy.getTo().toString());
        }
    }
}
