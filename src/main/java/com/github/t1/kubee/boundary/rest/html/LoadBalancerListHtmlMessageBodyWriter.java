package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.entity.LoadBalancer;
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
public class LoadBalancerListHtmlMessageBodyWriter implements MessageBodyWriter<List<LoadBalancer>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
            && ((ParameterizedType) genericType).getRawType().equals(List.class)
            && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(LoadBalancer.class);
    }

    @Override
    public long getSize(List<LoadBalancer> loadBalancers, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<LoadBalancer> loadBalancers, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(new LoadBalancersHtml(loadBalancers).toString());
        out.flush();
    }

    private class LoadBalancersHtml extends Html {
        private final List<LoadBalancer> loadBalancers;
        private Table table;

        private LoadBalancersHtml(List<LoadBalancer> loadBalancers) {
            this.loadBalancers = loadBalancers;

            header("Load-Balancing");
            container().with(new Panel().heading("Load-Balancing").with(table = new Table()));
            tableHeader();
            this.loadBalancers.forEach(this::loadBalancerRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th("Name");
            row.th("Method");
            row.th("Servers");
        }

        private void loadBalancerRow(LoadBalancer loadBalancer) {
            TableRow row = table.tr();
            row.td(loadBalancer.getName());
            row.td(loadBalancer.getMethod());
            row.td(String.join("<br>\n", loadBalancer.getServers()));
        }
    }
}
