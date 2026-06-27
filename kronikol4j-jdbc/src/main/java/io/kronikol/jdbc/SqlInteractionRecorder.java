package io.kronikol.jdbc;

import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.sql.SqlCommandType;
import io.kronikol.core.sql.UnifiedSqlClassifier;
import io.kronikol.core.sql.UnifiedSqlOperation;
import io.kronikol.core.sql.UnifiedSqlOperationInfo;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.PhaseVariant;
import io.kronikol.core.tracking.PhaseVariantExtensions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the request/response {@link RequestResponseLog} pair for a SQL command execution, with full parity
 * to the .NET {@code SqlDiagnosticTracker} ({@code LogRequest}/{@code LogResponse}). Two-phase: {@link
 * #logRequest} emits the request and returns a {@link Correlation} token; {@link #logResponse} emits the
 * matching response when the command completes. This is the reusable core that the JDBC {@code DataSource} /
 * {@code Connection} / {@code Statement} wrappers (and a Hibernate {@code StatementInspector}) delegate to.
 *
 * <p>It composes the cross-cutting infrastructure: {@link UnifiedSqlClassifier} (operation + table),
 * {@link PhaseConfiguration} (phase suppression + effective verbosity), {@link PhaseVariantExtensions}
 * (unknown-phase variants), and the {@link TrackingVerbosity} scale.
 */
public final class SqlInteractionRecorder {

    private final SqlTrackingOptions options;

    public SqlInteractionRecorder(SqlTrackingOptions options) {
        this.options = options;
    }

    /** Correlation token shared by a request and its later response. */
    public record Correlation(UUID traceId, UUID requestResponseId) {
    }

    /** Emits the request half; returns the correlation token, or empty when the command is not tracked. */
    public Optional<Correlation> logRequest(String commandText, String dataSource, String database,
                                            SqlCommandType commandType, String parameters) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return Optional.empty();
        }
        TrackingVerbosity ev = effectiveVerbosity();
        UnifiedSqlOperationInfo op = UnifiedSqlClassifier.classify(commandText, commandType);

        if (ev == TrackingVerbosity.SUMMARISED && op.operation() == UnifiedSqlOperation.OTHER) {
            return Optional.empty();
        }
        if (options.excludedOperations().contains(op.operation())) {
            return Optional.empty();
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return Optional.empty();
        }

        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(requestMethod(commandText, op, ev))
            .content(requestContent(commandText, parameters, ev))
            .uri(buildUri(dataSource, database, op, ev)).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).dependencyCategory(options.dependencyCategory()).phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> buildRequestVariant(commandText, dataSource, database, parameters, op, v));

        RequestResponseLogger.log(log);
        return Optional.of(new Correlation(traceId, requestResponseId));
    }

    /** Emits the response half with a row count (or an exception message). */
    public void logResponse(Correlation correlation, Integer rowsAffected, Throwable exception) {
        logResponse(correlation, rowsAffectedContent(rowsAffected, exception), exception, true);
    }

    /** Emits the response half with pre-formatted content (e.g. from a result-set summary). */
    public void logResponse(Correlation correlation, String content, Throwable exception) {
        logResponse(correlation, exception != null ? exception.getMessage() : content, exception, false);
    }

    private void logResponse(Correlation correlation, String rawContent, Throwable exception, boolean rowsForm) {
        if (correlation == null
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }

        String responseContent = ev == TrackingVerbosity.SUMMARISED && !options.logResponseContent()
            ? null
            : rawContent;
        StatusCode status = StatusCode.of(exception != null ? "Error" : "OK");
        TestPhase phase = TestPhaseContext.current();
        URI uri = URI.create(options.uriScheme() + ":///");

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(Method.of("")).content(responseContent)
            .uri(uri).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE)
            .traceId(correlation.traceId()).requestResponseId(correlation.requestResponseId())
            .trackingIgnore(false).statusCode(status).dependencyCategory(options.dependencyCategory())
            .phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> {
                String vContent = v == TrackingVerbosity.SUMMARISED && !options.logResponseContent()
                    ? null
                    : rawContent;
                return new PhaseVariant(Method.of(""), uri, vContent, List.of(), false);
            });

        RequestResponseLogger.log(log);
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    private static String rowsAffectedContent(Integer rowsAffected, Throwable exception) {
        if (exception != null) {
            return exception.getMessage();
        }
        return rowsAffected != null ? rowsAffected + " rows affected" : null;
    }

    private Method requestMethod(String commandText, UnifiedSqlOperationInfo op, TrackingVerbosity ev) {
        if (ev == TrackingVerbosity.RAW) {
            String keyword = UnifiedSqlClassifier.getRawKeyword(commandText);
            return Method.of(keyword != null ? keyword : "SQL");
        }
        return Method.of(UnifiedSqlClassifier.getDiagramLabel(op, ev));
    }

    private String requestContent(String commandText, String parameters, TrackingVerbosity ev) {
        if (ev == TrackingVerbosity.SUMMARISED) {
            return null;
        }
        if (ev == TrackingVerbosity.RAW && parameters != null) {
            return commandText + "\n-- Parameters: " + parameters;
        }
        return options.logSqlText() ? commandText : null;
    }

    private URI buildUri(String dataSource, String database, UnifiedSqlOperationInfo op, TrackingVerbosity ev) {
        String db = (database == null || database.isEmpty()) ? "unknown" : database;
        String ds = (dataSource == null || dataSource.isEmpty()) ? "localhost" : dataSource;
        ds = ds.replace(',', ':'); // SQL Server uses comma for ports; URI needs colon
        String scheme = options.uriScheme();
        String table = op.tableName();
        return switch (ev) {
            case RAW -> URI.create(scheme + "://" + ds + "/" + db);
            case SUMMARISED -> table != null
                ? URI.create(scheme + ":///" + db + "/" + table)
                : URI.create(scheme + ":///" + db);
            default -> table != null
                ? URI.create(scheme + "://" + ds + "/" + db + "/" + table)
                : URI.create(scheme + "://" + ds + "/" + db);
        };
    }

    private PhaseVariant buildRequestVariant(String commandText, String dataSource, String database,
                                             String parameters, UnifiedSqlOperationInfo op, TrackingVerbosity v) {
        boolean skip = v == TrackingVerbosity.SUMMARISED && op.operation() == UnifiedSqlOperation.OTHER;
        return new PhaseVariant(requestMethod(commandText, op, v), buildUri(dataSource, database, op, v),
            requestContent(commandText, parameters, v), List.of(), skip);
    }
}
