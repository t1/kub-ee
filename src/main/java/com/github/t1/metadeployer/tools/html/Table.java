package com.github.t1.metadeployer.tools.html;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;

public class Table {
    private final Element table;

    public Table(Element body) {
        this.table = body
                .appendElement("div").addClass("table-responsive")
                .appendElement("table").addClass("table table-striped")
                .appendElement("tbody");
    }

    public TableRow tr() { return new TableRow(table.appendElement("tr")); }

    @RequiredArgsConstructor
    public static class TableRow {
        private final Element row;

        public Element th() { return row.appendElement("th"); }

        public Element td() { return row.appendElement("td"); }
    }
}
