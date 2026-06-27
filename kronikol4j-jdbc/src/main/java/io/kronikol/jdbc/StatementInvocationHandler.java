package io.kronikol.jdbc;

import io.kronikol.core.sql.SqlCommandType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;

/**
 * Proxies a {@link Statement}/{@link PreparedStatement}, recording the typed execution methods
 * ({@code executeQuery}, {@code executeUpdate}, {@code executeLargeUpdate}) via
 * {@link SqlInteractionRecorder}. For {@code executeQuery} the returned {@link ResultSet} is itself proxied
 * so the response (row count + columns) is captured when the result set is exhausted or closed.
 *
 * <p>Untyped {@code execute(...)} / {@code executeBatch()} are delegated without tracking for now (a tracked
 * follow-up); the typed methods cover JdbcTemplate, JPA/Hibernate and the overwhelming majority of usage.
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
