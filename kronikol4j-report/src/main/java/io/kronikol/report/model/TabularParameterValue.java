package io.kronikol.report.model;

import java.util.List;

/** A tabular step-parameter value with column definitions and data rows
 *  (mirrors the .NET {@code TabularParameterValue} + its column/row/cell records). */
public record TabularParameterValue(List<TabularColumn> columns, List<TabularRow> rows, boolean isLinkedOutput) {

    public TabularParameterValue {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public TabularParameterValue(List<TabularColumn> columns, List<TabularRow> rows) {
        this(columns, rows, false);
    }

    /** A column in a tabular step parameter. */
    public record TabularColumn(String name, boolean isKey) {
    }

    /** A row in a tabular step parameter. */
    public record TabularRow(TableRowType type, List<TabularCell> values) {
        public TabularRow {
            values = values == null ? List.of() : List.copyOf(values);
        }
    }

    /** A cell in a tabular step parameter row. */
    public record TabularCell(String value, String expectation, VerificationStatus status) {
    }
}
