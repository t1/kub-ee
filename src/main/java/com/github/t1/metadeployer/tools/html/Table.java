package com.github.t1.metadeployer.tools.html;

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

        public Element th() { return row.appendElement("th"); }

        public Element td() { return row.appendElement("td"); }

        public TableRow attr(String name, String value) {
            row.attr(name, value);
            return this;
        }
    }
}
