package com.github.t1.kubee.tools.html;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;

public class Table extends Component {
    private String id;

    public Table id(String id) {
        this.id = id;
        return this;
    }

    @Override protected void addTo(Component parent) {
        Element table = parent.element
                .appendElement("div").addClass("table-responsive")
                .appendElement("table").addClass("table table-striped");
        this.element = table.appendElement("t" + "body");
        if (id != null)
            table.attr("id", id);
    }

    public TableRow tr() { return new TableRow(this.element.appendElement("tr")); }

    @RequiredArgsConstructor
    public static class TableRow {
        private final Element row;

        public TableCell th(String header) { return new TableCell("th", header); }

        public TableCell td(Component component) { return new TableCell("td", component); }

        public TableCell td(String text) { return new TableCell("td", text); }

        public TableCell td(Integer text) { return new TableCell("td", text.toString()); }

        public TableRow attr(String name, String value) {
            row.attr(name, value);
            return this;
        }

        public class TableCell {
            private final Element element;

            private TableCell(String tag, Component component) {
                this(tag);
                this.element.appendChild(component.element);
            }

            private TableCell(String tag, String header) {
                this(tag);
                this.element.html(((header == null) || header.isEmpty()) ? "&nbsp;" : header);
            }

            private TableCell(String tag) { this.element = TableRow.this.row.appendElement(tag); }

            public TableCell attr(String key, Object value) {
                element.attr(key, (value == null) ? null : value.toString());
                return this;
            }

            public TableCell className(String className) {
                element.addClass(className);
                return this;
            }
        }
    }
}
