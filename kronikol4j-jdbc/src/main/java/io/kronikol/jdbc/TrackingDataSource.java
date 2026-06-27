package io.kronikol.jdbc;

import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * A {@link DataSource} decorator that auto-captures SQL executions made through any connection it hands out.
 * Wrap a real data source: {@code DataSource tracked = TrackingDataSource.wrap(realDs, options);}. The
 * returned connections proxy {@link java.sql.Statement}/{@link java.sql.PreparedStatement} so that
 * {@code executeQuery}/{@code executeUpdate}/{@code executeLargeUpdate} are recorded via
 * {@link SqlInteractionRecorder}, with {@link java.sql.ResultSet} row/column response capture.
 *
 * <p>One JDBC-level tracker covers every relational database (plan §2): the {@link io.kronikol.core.sql}
 * classifier already understands the dialects. Connection/Statement/ResultSet are wrapped with dynamic
 * {@link Proxy} instances rather than hand-written delegates (the JDBC interfaces have hundreds of methods).
 */
public final class TrackingDataSource implements DataSource {

    private final DataSource delegate;
    private final SqlTrackingOptions options;
    private final SqlInteractionRecorder recorder;

    private TrackingDataSource(DataSource delegate, SqlTrackingOptions options) {
        this.delegate = delegate;
        this.options = options;
        this.recorder = new SqlInteractionRecorder(options);
    }

    /** Wraps {@code delegate} so executions through its connections are tracked. */
    public static DataSource wrap(DataSource delegate, SqlTrackingOptions options) {
        return new TrackingDataSource(delegate, options);
    }

    @Override
    public Connection getConnection() throws SQLException {
        return wrapConnection(delegate.getConnection());
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return wrapConnection(delegate.getConnection(username, password));
    }

    private Connection wrapConnection(Connection real) {
        String database = catalogOf(real);
        ConnectionInvocationHandler handler =
            new ConnectionInvocationHandler(real, recorder, options, null, database);
        return (Connection) Proxy.newProxyInstance(
            Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, handler);
    }

    private static String catalogOf(Connection real) {
        try {
            return real.getCatalog();
        } catch (SQLException e) {
            return null; // recorder falls back to "unknown"
        }
    }

    // --- DataSource delegation ----------------------------------------------------------------------

    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() { throw new UnsupportedOperationException("getParentLogger"); }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface);
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        return iface.isInstance(this) || delegate.isWrapperFor(iface);
    }
}
