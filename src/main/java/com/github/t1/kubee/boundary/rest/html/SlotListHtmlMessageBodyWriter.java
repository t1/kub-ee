package com.github.t1.kubee.boundary.rest.html;

import com.github.t1.kubee.entity.Slot;
import com.github.t1.kubee.tools.html.Html;
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
public class SlotListHtmlMessageBodyWriter implements MessageBodyWriter<List<Slot>> {
    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return genericType instanceof ParameterizedType
            && ((ParameterizedType) genericType).getRawType().equals(List.class)
            && ((ParameterizedType) genericType).getActualTypeArguments()[0].equals(Slot.class);
    }

    @Override
    public long getSize(List<Slot> slots, Class<?> type, Type genericType, Annotation[] annotations,
                        MediaType mediaType) {
        return -1;
    }

    @Override
    public void writeTo(List<Slot> slots, Class<?> type, Type genericType,
                        Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders,
                        OutputStream entityStream) throws IOException, WebApplicationException {
        @SuppressWarnings("resource") OutputStreamWriter out = new OutputStreamWriter(entityStream);
        out.write(new SlotsHtml(slots).toString());
        out.flush();
    }

    private class SlotsHtml extends Html {
        private final List<Slot> slots;
        private Table table;

        private SlotsHtml(List<Slot> slots) {
            this.slots = slots;

            header("Slots");
            container().fluid().with(table = new Table());
            tableHeader();
            this.slots.forEach(this::slotRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th("Slot");
            row.th("http");
            row.th("https");
        }

        private void slotRow(Slot slot) {
            TableRow row = table.tr();
            row.th(slot.getName());
            row.td(slot.getHttp());
            row.td(slot.getHttps());
        }
    }
}
