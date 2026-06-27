package io.kronikol.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/**
 * Proxies a {@link ResultSet}, counting rows as {@code next()} advances and emitting the SQL response
 * (row count + column names, via {@link SqlResultSummary}) exactly once — when the result set is exhausted
 * ({@code next()} returns {@code false}) or {@code close()}d. The Java analog of .NET
 * {@code TrackingDbDataReader}.
 */
final class ResultSetInvocationHandler implements InvocationHandler {

    private final ResultSet real;
    private final SqlInteractionRecorder recorder;
    private final SqlTrackingOptions options;
    private final SqlInteractionRecorder.Correlation correlation;
    private final List<String> columnNames;

    private int rowCount;
    private boolean logged;

    ResultSetInvocationHandler(ResultSet real, SqlInteractionRecorder recorder, SqlTrackingOptions options,
                               SqlInteractionRecorder.Correlation correlation) {
        this.real = real;
        this.recorder = recorder;
        this.options = options;
        this.correlation = correlation;
        this.columnNames = captureColumnNames(real);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            Object result = method.invoke(real, args);
            if ("next".equals(name)) {
                if (Boolean.TRUE.equals(result)) {
                    rowCount++;
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

    private void finish() {
        if (logged) {
            return;
        }
        logged = true;
        recorder.logResponse(correlation, SqlResultSummary.format(rowCount, columnNames, options.responseDetail()), null);
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
