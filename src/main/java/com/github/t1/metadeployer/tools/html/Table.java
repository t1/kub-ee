package com.github.t1.metadeployer.tools.html;

import lombok.RequiredArgsConstructor;
import org.jsoup.nodes.Element;

public class Table {
    private final Element table;
    private final Element tableBody;

    public Table(Element body) {
        this.table = body
                .appendElement("div").addClass("table-responsive")
                .appendElement("table").addClass("table table-striped");
        this.tableBody = table.appendElement("t" + "body");
    }

    public Table id(String id) {
        table.attr("id", id);
        return this;
    }

    public TableRow tr() { return new TableRow(tableBody.appendElement("tr")); }

    @RequiredArgsConstructor
    public static class TableRow {
        private final Element row;

        public Element th() { return row.appendElement("th"); }

        public Element td() { return row.appendElement("td"); }
    }
}
