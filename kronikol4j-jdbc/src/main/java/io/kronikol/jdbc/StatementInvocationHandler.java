package io.kronikol.jdbc;

import io.kronikol.core.sql.SqlCommandType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/**
 * Proxies a {@link Statement}/{@link PreparedStatement}, recording the execution methods via
 * {@link SqlInteractionRecorder}: the typed {@code executeQuery} (the returned {@link ResultSet} is itself
 * proxied so the response row-count + columns are captured on exhaustion/close), {@code executeUpdate}/
 * {@code executeLargeUpdate}, the untyped {@code execute(...)} (response row count read back via
 * {@code getUpdateCount()}, or unknown when it produced a {@link ResultSet}), and {@code executeBatch}/
 * {@code executeLargeBatch} for prepared statements (response = the summed per-statement counts). Together
 * these cover JdbcTemplate, JPA/Hibernate batch inserts, and direct JDBC usage.
 */
final class StatementInvocationHandler implements InvocationHandler {

    private final Statement real;
    private final SqlInteractionRecorder recorder;
    private final SqlTrackingOptions options;
    private final String dataSource;
    private final String database;
    private final String preparedSql;

    StatementInvocationHandler(Statement real, SqlInteractionRecorder recorder, SqlTrackingOptions options,
                               String dataSource, String database, String preparedSql) {
        this.real = real;
        this.recorder = recorder;
        this.options = options;
        this.dataSource = dataSource;
        this.database = database;
        this.preparedSql = preparedSql;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            return switch (name) {
                case "executeQuery" -> executeQuery(method, args);
                case "executeUpdate", "executeLargeUpdate" -> executeUpdate(method, args);
                case "execute" -> execute(method, args);
                case "executeBatch", "executeLargeBatch" -> executeBatch(method, args);
                default -> method.invoke(real, args);
            };
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Object executeQuery(Method method, Object[] args) throws Throwable {
        String sql = sqlFrom(args);
        Optional<SqlInteractionRecorder.Correlation> corr = logRequest(sql);
        try {
            ResultSet rs = (ResultSet) method.invoke(real, args);
            if (corr.isEmpty() || rs == null) {
                return rs;
            }
            return wrapResultSet(rs, corr.get());
        } catch (InvocationTargetException e) {
            corr.ifPresent(c -> recorder.logResponse(c, (Integer) null, e.getCause()));
            throw e.getCause();
        }
    }

    private Object executeUpdate(Method method, Object[] args) throws Throwable {
        String sql = sqlFrom(args);
        Optional<SqlInteractionRecorder.Correlation> corr = logRequest(sql);
        try {
            Object count = method.invoke(real, args);
            corr.ifPresent(c -> recorder.logResponse(c, ((Number) count).intValue(), null));
            return count;
        } catch (InvocationTargetException e) {
            corr.ifPresent(c -> recorder.logResponse(c, (Integer) null, e.getCause()));
            throw e.getCause();
        }
    }

    /** Untyped {@code execute(...)}: the response row count is read back via {@code getUpdateCount()}
     *  ({@code -1} → the statement produced a {@link ResultSet}, so the count is left unknown). */
    private Object execute(Method method, Object[] args) throws Throwable {
        String sql = sqlFrom(args);
        Optional<SqlInteractionRecorder.Correlation> corr = logRequest(sql);
        try {
            Object result = method.invoke(real, args);
            corr.ifPresent(c -> recorder.logResponse(c, updateCountOrNull(), null));
            return result;
        } catch (InvocationTargetException e) {
            corr.ifPresent(c -> recorder.logResponse(c, (Integer) null, e.getCause()));
            throw e.getCause();
        }
    }

    /** {@code executeBatch}/{@code executeLargeBatch} for a prepared statement: response = summed counts. */
    private Object executeBatch(Method method, Object[] args) throws Throwable {
        Optional<SqlInteractionRecorder.Correlation> corr =
            preparedSql != null ? logRequest(preparedSql) : Optional.empty();
        try {
            Object counts = method.invoke(real, args);
            corr.ifPresent(c -> recorder.logResponse(c, sumBatchCounts(counts), null));
            return counts;
        } catch (InvocationTargetException e) {
            corr.ifPresent(c -> recorder.logResponse(c, (Integer) null, e.getCause()));
            throw e.getCause();
        }
    }

    /** The current result's update count, or {@code null} when it is a {@link ResultSet} / unavailable. */
    private Integer updateCountOrNull() {
        try {
            int uc = real.getUpdateCount();
            return uc >= 0 ? uc : null;
        } catch (SQLException ignored) {
            return null;
        }
    }

    /** Sums the positive per-statement batch counts (skips {@code SUCCESS_NO_INFO}/{@code EXECUTE_FAILED}). */
    private static Integer sumBatchCounts(Object counts) {
        int total = 0;
        if (counts instanceof int[] arr) {
            for (int v : arr) {
                if (v > 0) {
                    total += v;
                }
            }
        } else if (counts instanceof long[] arr) {
            for (long v : arr) {
                if (v > 0) {
                    total += (int) v;
                }
            }
        }
        return total;
    }

    private Optional<SqlInteractionRecorder.Correlation> logRequest(String sql) {
        return recorder.logRequest(sql, dataSource, database, SqlCommandType.TEXT, null);
    }

    /** The SQL: the prepared text for a PreparedStatement, else the first execute(...) argument. */
    private String sqlFrom(Object[] args) {
        if (preparedSql != null) {
            return preparedSql;
        }
        return args != null && args.length > 0 && args[0] instanceof String s ? s : null;
    }

    private ResultSet wrapResultSet(ResultSet rs, SqlInteractionRecorder.Correlation corr) {
        ResultSetInvocationHandler handler = new ResultSetInvocationHandler(rs, recorder, options, corr);
        return (ResultSet) Proxy.newProxyInstance(
            ResultSet.class.getClassLoader(), new Class<?>[] {ResultSet.class}, handler);
    }
}
