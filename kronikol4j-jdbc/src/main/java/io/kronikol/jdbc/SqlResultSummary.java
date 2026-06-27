package io.kronikol.jdbc;

import java.util.List;

/**
 * Formats a result-set summary string for the SQL response arrow, porting the relevant parts of .NET
 * {@code TrackingDbDataReader.FormatContent}/{@code GetColumnNames}: {@code "1 row"} / {@code "N rows"} for
 * {@link SqlResponseDetail#ROW_COUNT_ONLY}, and {@code "N rows [Col1, Col2]"} for
 * {@link SqlResponseDetail#ROW_COUNT_AND_COLUMNS} (columns truncated past 20 with a {@code "... (+N more)"}
 * suffix).
 *
 * <p>{@link SqlResponseDetail#FULL_ROWS} (cell-level JSON) is not yet implemented — it falls back to the
 * column format; full row-data capture is a tracked JDBC follow-up.
 */
public final class SqlResultSummary {

    private static final int MAX_DISPLAYED_COLUMNS = 20;

    private SqlResultSummary() {
    }

    public static String format(int totalRows, List<String> columnNames, SqlResponseDetail detail) {
        String rowLabel = totalRows == 1 ? "1 row" : totalRows + " rows";
        if (detail == SqlResponseDetail.ROW_COUNT_ONLY) {
            return rowLabel;
        }
        String columns = formatColumns(columnNames);
        return columns != null ? rowLabel + " [" + columns + "]" : rowLabel;
    }

    private static String formatColumns(List<String> names) {
        if (names == null || names.isEmpty()) {
            return null;
        }
        if (names.size() <= MAX_DISPLAYED_COLUMNS) {
            return String.join(", ", names);
        }
        String displayed = String.join(", ", names.subList(0, MAX_DISPLAYED_COLUMNS));
        return displayed + " ... (+" + (names.size() - MAX_DISPLAYED_COLUMNS) + " more)";
    }
}
