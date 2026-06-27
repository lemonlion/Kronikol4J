package io.kronikol.hibernate;

import io.kronikol.core.sql.SqlCommandType;
import io.kronikol.jdbc.SqlInteractionRecorder;
import io.kronikol.jdbc.SqlTrackingOptions;
import java.util.Optional;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * A Hibernate {@link StatementInspector} that records every SQL statement Hibernate issues as a tracked
 * diagram interaction — the ORM integration point (the Java analog of the .NET EF Core
 * {@code SqlTrackingInterceptor}). Register it via
 * {@code hibernate.session_factory.statement_inspector} (or
 * {@code properties.put(AvailableSettings.STATEMENT_INSPECTOR, inspector)}).
 *
 * <p>Each inspected statement is classified with the shared {@code UnifiedSqlClassifier} and emitted as a
 * request/response pair through the reused {@link SqlInteractionRecorder}, honouring verbosity, phase
 * filtering, excluded operations and identity resolution exactly as the raw-JDBC adapter does. The SQL is
 * returned unchanged (this inspector never rewrites statements).
 *
 * <p><b>Scope of the hook.</b> {@code StatementInspector} is a SQL-text hook invoked when Hibernate prepares
 * a statement; it carries no execution result, so the recorded response has no row count. For full two-phase
 * capture with row counts / result-set summaries, wrap the JPA {@code DataSource} with the JDBC module's
 * {@code TrackingDataSource} instead (or in addition). The {@code dataSource}/{@code database} names used in
 * the diagram URI are supplied at construction (Hibernate does not expose connection metadata here).
 */
public final class KronikolStatementInspector implements StatementInspector {

    private final SqlInteractionRecorder recorder;
    private final String dataSource;
    private final String database;

    /** Inspector using the option-derived defaults for the URI host/database. */
    public KronikolStatementInspector(SqlTrackingOptions options) {
        this(options, null, options == null ? null : options.serviceName());
    }

    /**
     * @param options    the SQL tracking options (verbosity, phase, service/caller names, URI scheme, …)
     * @param dataSource the data-source/host shown in the diagram URI ({@code null} → {@code "localhost"})
     * @param database   the database name shown in the diagram URI ({@code null} → {@code "unknown"})
     */
    public KronikolStatementInspector(SqlTrackingOptions options, String dataSource, String database) {
        this.recorder = new SqlInteractionRecorder(options);
        this.dataSource = dataSource;
        this.database = database;
    }

    @Override
    public String inspect(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        Optional<SqlInteractionRecorder.Correlation> correlation =
            recorder.logRequest(sql, dataSource, database, SqlCommandType.TEXT, null);
        // StatementInspector has no execution-completion callback, so complete the pair immediately with
        // no row count (a complete, result-less interaction). Full row capture uses TrackingDataSource.
        correlation.ifPresent(c -> recorder.logResponse(c, (Integer) null, null));
        return sql;
    }
}
