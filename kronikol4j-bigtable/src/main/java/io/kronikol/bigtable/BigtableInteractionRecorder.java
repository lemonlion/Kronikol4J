package io.kronikol.bigtable;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.PhaseVariant;
import io.kronikol.core.tracking.PhaseVariantExtensions;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the request/response {@link RequestResponseLog} pair for a Bigtable operation, with full parity to
 * the .NET {@code BigtableTracker} ({@code LogRequest}/{@code LogResponse}). The request half is event-styled
 * ({@link RequestResponseMetaType#EVENT}); both halves carry the classified label, the
 * {@code bigtable:///table} URI, phase suppression, excluded-operation filtering, the Summarised content drop
 * and phase variants. This is the reusable core a Bigtable SDK hook delegates to.
 */
public final class BigtableInteractionRecorder {

    private final BigtableTrackerOptions options;

    public BigtableInteractionRecorder(BigtableTrackerOptions options) {
        this.options = options;
    }

    /** Correlation token shared by a request and its later response. */
    public record Correlation(UUID traceId, UUID requestResponseId) {
    }

    /** Emits the request half (event-styled); returns the correlation token, or empty when not tracked. */
    public Optional<Correlation> logRequest(BigtableOperationInfo operation, String content) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())
            || options.excludedOperations().contains(operation.operation())) {
            return Optional.empty();
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return Optional.empty();
        }
        TrackingVerbosity ev = effectiveVerbosity();
        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(label(operation, ev))
            .content(ev == TrackingVerbosity.SUMMARISED ? null : content)
            .uri(buildUri(operation, ev)).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(RequestResponseMetaType.EVENT)
            .dependencyCategory(DependencyCategories.BIGTABLE).phase(phase).build();

        attachVariants(log, operation, content);
        RequestResponseLogger.log(log);
        return Optional.of(new Correlation(traceId, requestResponseId));
    }

    /** Emits the response half, correlated to a prior request. */
    public void logResponse(BigtableOperationInfo operation, Correlation correlation, String content) {
        if (correlation == null
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())
            || options.excludedOperations().contains(operation.operation())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(label(operation, ev))
            .content(ev == TrackingVerbosity.SUMMARISED ? null : content)
            .uri(buildUri(operation, ev)).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE)
            .traceId(correlation.traceId()).requestResponseId(correlation.requestResponseId())
            .trackingIgnore(false).statusCode(StatusCode.of("OK"))
            .dependencyCategory(DependencyCategories.BIGTABLE).phase(phase).build();

        attachVariants(log, operation, content);
        RequestResponseLogger.log(log);
    }

    private void attachVariants(RequestResponseLog log, BigtableOperationInfo operation, String content) {
        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(label(operation, v), buildUri(operation, v),
                v == TrackingVerbosity.SUMMARISED ? null : content, List.of(), false));
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    private static Method label(BigtableOperationInfo op, TrackingVerbosity ev) {
        return Method.of(BigtableOperationClassifier.getDiagramLabel(op, ev));
    }

    private static URI buildUri(BigtableOperationInfo op, TrackingVerbosity ev) {
        if (ev == TrackingVerbosity.RAW) {
            return op.tableName() != null
                ? URI.create("bigtable:///" + op.tableName())
                : URI.create("bigtable:///unknown");
        }
        String table = shortTableNameOrNull(op.tableName());
        return table != null
            ? URI.create("bigtable:///" + table)
            : URI.create("bigtable:///unknown");
    }

    private static String shortTableNameOrNull(String fullName) {
        if (fullName == null) {
            return null;
        }
        int lastSlash = fullName.lastIndexOf('/');
        return lastSlash >= 0 ? fullName.substring(lastSlash + 1) : fullName;
    }
}
