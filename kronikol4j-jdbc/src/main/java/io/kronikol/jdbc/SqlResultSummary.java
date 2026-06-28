package io.kronikol.jdbc;

import java.util.List;
import java.util.Map;

/**
 * Formats a result-set summary string for the SQL response arrow, porting .NET
 * {@code TrackingDbDataReader.FormatContent}/{@code GetColumnNames}: {@code "1 row"} / {@code "N rows"} for
 * {@link SqlResponseDetail#ROW_COUNT_ONLY}, {@code "N rows [Col1, Col2]"} for
 * {@link SqlResponseDetail#ROW_COUNT_AND_COLUMNS} (columns truncated past 20 with a {@code "... (+N more)"}
 * suffix), and the cell-level JSON of the captured rows for {@link SqlResponseDetail#FULL_ROWS}.
 *
 * <p>{@code FULL_ROWS} renders the captured rows as compact JSON ({@code WriteIndented = false}) with
 * {@code UnsafeRelaxedJsonEscaping} and <em>null cells kept</em> — exactly the .NET {@code JsonSerializer}
 * settings (which do not drop nulls). When more rows were read than {@code maxResponseRows}, a
 * {@code "\n... (N more rows not shown)"} trailer is appended. With {@code maxResponseRows == 0} it falls back
 * to the column format (matching .NET). Cell scalars that are cross-runtime stable (null / string / integral
 * &amp; decimal numbers / boolean) are byte-identical to .NET; platform-specific types (temporal, LOB) render
 * via the runtime's natural form — the same documented boundary as reflection-based value rendering.
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

    /**
     * The {@link SqlResponseDetail#FULL_ROWS} content: compact cell-level JSON of {@code capturedRows} (up to
     * {@code maxRows}), with a "more rows" trailer when {@code totalRows > maxRows}. With {@code maxRows == 0}
     * this degrades to the column format, mirroring .NET.
     */
    public static String formatFullRows(int totalRows, List<String> columnNames,
                                        List<Map<String, Object>> capturedRows, int maxRows) {
        String rowLabel = totalRows == 1 ? "1 row" : totalRows + " rows";
        if (maxRows == 0) { // .NET forces the column format when maxResponseRows is 0
            String columns = formatColumns(columnNames);
            return columns != null ? rowLabel + " [" + columns + "]" : rowLabel;
        }
        StringBuilder sb = new StringBuilder();
        if (capturedRows != null && !capturedRows.isEmpty()) {
            writeRowsJson(capturedRows, sb);
        } else {
            sb.append(rowLabel);
        }
        if (totalRows > maxRows) {
            sb.append("\n... (").append(totalRows - maxRows).append(" more rows not shown)");
        }
        return sb.toString();
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

    // --- compact JSON for captured rows (List<Map<String,Object>>) -----------------------------------
    // Matches System.Text.Json with WriteIndented=false, UnsafeRelaxedJsonEscaping, nulls kept.

    private static void writeRowsJson(List<Map<String, Object>> rows, StringBuilder sb) {
        sb.append('[');
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sb.append(',');
            }
            writeRow(rows.get(r), sb);
        }
        sb.append(']');
    }

    private static void writeRow(Map<String, Object> row, StringBuilder sb) {
        sb.append('{');
        int i = 0;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (i++ > 0) {
                sb.append(',');
            }
            writeString(e.getKey(), sb);
            sb.append(':');
            writeCell(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeCell(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null"); // null cells are kept (DefaultIgnoreCondition = Never)
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue() ? "true" : "false");
        } else if (value instanceof Number n) {
            sb.append(String.valueOf(n));
        } else {
            writeString(value.toString(), sb); // strings + platform types → quoted
        }
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c); // UnsafeRelaxed: < > & + and non-ASCII pass through
                    }
                }
            }
        }
        sb.append('"');
    }
}
