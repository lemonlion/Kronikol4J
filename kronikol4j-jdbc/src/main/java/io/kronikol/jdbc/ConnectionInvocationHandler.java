package io.kronikol.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Proxies a {@link Connection}, returning tracking {@link Statement}/{@link PreparedStatement} proxies for
 * {@code createStatement} / {@code prepareStatement} / {@code prepareCall} and delegating everything else.
 */
final class ConnectionInvocationHandler implements InvocationHandler {

    private final Connection real;
    private final SqlInteractionRecorder recorder;
    private final SqlTrackingOptions options;
    private final String dataSource;
    private final String database;

    ConnectionInvocationHandler(Connection real, SqlInteractionRecorder recorder, SqlTrackingOptions options,
                                String dataSource, String database) {
        this.real = real;
        this.recorder = recorder;
        this.options = options;
        this.dataSource = dataSource;
        this.database = database;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        try {
            Object result = method.invoke(real, args);
            return switch (name) {
                case "createStatement" -> wrapStatement((Statement) result, null);
                case "prepareStatement" -> wrapPrepared((PreparedStatement) result, (String) args[0]);
                case "prepareCall" -> wrapCall((CallableStatement) result, (String) args[0]);
                default -> result;
            };
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private Statement wrapStatement(Statement real, String preparedSql) {
        return (Statement) Proxy.newProxyInstance(Statement.class.getClassLoader(),
            new Class<?>[] {Statement.class}, handler(real, preparedSql));
    }

    private PreparedStatement wrapPrepared(PreparedStatement real, String preparedSql) {
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
            new Class<?>[] {PreparedStatement.class}, handler(real, preparedSql));
    }

    private CallableStatement wrapCall(CallableStatement real, String preparedSql) {
        return (CallableStatement) Proxy.newProxyInstance(CallableStatement.class.getClassLoader(),
            new Class<?>[] {CallableStatement.class}, handler(real, preparedSql));
    }

    private StatementInvocationHandler handler(Statement real, String preparedSql) {
        return new StatementInvocationHandler(real, recorder, options, dataSource, database, preparedSql);
    }
}
