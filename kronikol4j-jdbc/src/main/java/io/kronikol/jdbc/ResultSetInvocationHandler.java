package io.kronikol.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Proxies a {@link ResultSet}, counting rows as {@code next()} advances and emitting the SQL response
 * exactly once — when the result set is exhausted ({@code next()} returns {@code false}) or {@code close()}d.
 * For {@link SqlResponseDetail#FULL_ROWS} it also captures each row's cells (up to {@code maxResponseRows})
 * as it is read, so the response carries the cell-level JSON. The Java analog of .NET
 * {@code TrackingDbDataReader}.
 */
final class ResultSetInvocationHandler implements InvocationHandler {

    private final ResultSet real;
    private final SqlInteractionRecorder recorder;
    private final SqlTrackingOptions options;
    private final SqlInteractionRecorder.Correlation correlation;
    private final List<String> columnNames;
    private final boolean captureRows;
    private final List<Map<String, Object>> capturedRows = new ArrayList<>();

    private int rowCount;
    private boolean logged;

    ResultSetInvocationHandler(ResultSet real, SqlInteractionRecorder recorder, SqlTrackingOptions options,
                               SqlInteractionRecorder.Correlation correlation) {
        this.real = real;
        this.recorder = recorder;
        this.options = options;
        this.correlation = correlation;
        this.columnNames = captureColumnNames(real);
        // FULL_ROWS captures cells; with maxResponseRows == 0 .NET degrades to the column format (no capture).
        this.captureRows = options.responseDetail() == SqlResponseDetail.FULL_ROWS
            && options.maxResponseRows() > 0;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            Object result = method.invoke(real, args);
            if ("next".equals(name)) {
                if (Boolean.TRUE.equals(result)) {
                    rowCount++;
                    captureCurrentRowIfNeeded();
                } else {
                    finish(); // exhausted
                }
            } else if ("close".equals(name)) {
                finish();
            }
            return result;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Captures the current row's cells when FULL_ROWS is on and the maxRows budget is not yet spent. */
    private void captureCurrentRowIfNeeded() {
        if (!captureRows || capturedRows.size() >= options.maxResponseRows()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < columnNames.size(); i++) {
            String name = columnNames.get(i);
            try {
                Object value = real.getObject(i + 1);
                row.put(name, value == null ? null : formatCellValue(value));
            } catch (Exception e) {
                row.put(name, null); // a cell that can't be read degrades to null rather than failing capture
            }
        }
        capturedRows.add(row);
    }

    /** Mirrors .NET {@code FormatCellValue}: byte[] → marker, over-long strings truncated, else the raw value. */
    private Object formatCellValue(Object value) {
        if (value instanceof byte[] bytes) {
            return "[bytes: " + bytes.length + "]";
        }
        String str = value.toString();
        int max = options.maxValueDisplayLength();
        if (max >= 0 && str.length() > max) {
            return str.substring(0, max) + "... (" + str.length() + " chars)";
        }
        return value;
    }

    private void finish() {
        if (logged) {
            return;
        }
        logged = true;
        String content = options.responseDetail() == SqlResponseDetail.FULL_ROWS
            ? SqlResultSummary.formatFullRows(rowCount, columnNames, capturedRows, options.maxResponseRows())
            : SqlResultSummary.format(rowCount, columnNames, options.responseDetail());
        recorder.logResponse(correlation, content, null);
    }

    private static List<String> captureColumnNames(ResultSet rs) {
        try {
            ResultSetMetaData md = rs.getMetaData();
            int count = md.getColumnCount();
            List<String> names = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                names.add(md.getColumnLabel(i));
            }
            return names;
        } catch (Exception e) {
            return List.of(); // metadata unavailable — degrade to row-count-only
        }
    }
}
