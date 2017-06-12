package com.github.t1.metadeployer.boundary;

import com.github.t1.metadeployer.tools.html.*;
import com.github.t1.metadeployer.tools.html.Table.TableRow;
import com.github.t1.metadeployer.model.Slot;

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
            table = withoutContainer().table();
            tableHeader();
            this.slots.forEach(this::slotRow);
        }

        private void tableHeader() {
            TableRow row = table.tr();
            row.th().text("Slot");
            row.th().text("http");
            row.th().text("https");
        }

        private void slotRow(Slot slot) {
            TableRow row = table.tr();
            row.th().text(slot.getName());
            row.td().text(Integer.toString(slot.getHttp()));
            row.td().text(Integer.toString(slot.getHttps()));
        }
    }
}
