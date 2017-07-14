package com.github.t1.metadeployer.boundary.html;

import com.github.t1.metadeployer.model.LoadBalancer;
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
            container().with(
                    new Panel().heading("Load-Balancing")
                               .with(
                                       table = new Table()
                               )
            );
            tableHeader();
            this.loadBalancers.forEach(this::loadBalancerRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th().text("from");
            row.th().text("to");
        }

        private void loadBalancerRow(LoadBalancer loadBalancer) {
            TableRow row = table.tr();
            row.td().text(loadBalancer.getFrom().toString());
            row.td().text(loadBalancer.getTo().toString());
        }
    }
}
