package io.kronikol.eventhubs;

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
 * Builds the request/response {@link RequestResponseLog} pair for an Azure Event Hubs operation, with full
 * parity to the .NET {@code EventHubsTracker} ({@code LogRequest}/{@code LogResponse}). The request half is
 * event-styled ({@link RequestResponseMetaType#EVENT}); both halves carry the classified label, the
 * {@code eventhubs:///hub[/partition]} URI, the {@code MessageQueue} category (queue shape), phase
 * suppression, the Summarised content drop and phase variants. This is the reusable core the
 * producer/consumer client wrappers delegate to.
 */
public final class EventHubsInteractionRecorder {

    private final EventHubsTrackerOptions options;

    public EventHubsInteractionRecorder(EventHubsTrackerOptions options) {
        this.options = options;
    }

    /** Correlation token shared by a request and its later response. */
    public record Correlation(UUID traceId, UUID requestResponseId) {
    }

    /** Emits the request half (event-styled); returns the correlation token, or empty when not tracked. */
    public Optional<Correlation> logRequest(EventHubsOperationInfo operation, String content) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
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
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();

        attachVariants(log, operation, content);
        RequestResponseLogger.log(log);
        return Optional.of(new Correlation(traceId, requestResponseId));
    }

    /** Emits the response half, correlated to a prior request. */
    public void logResponse(EventHubsOperationInfo operation, Correlation correlation, String content) {
        if (correlation == null
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
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
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();

        attachVariants(log, operation, content);
        RequestResponseLogger.log(log);
    }

    private void attachVariants(RequestResponseLog log, EventHubsOperationInfo operation, String content) {
        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(label(operation, v), buildUri(operation, v),
                v == TrackingVerbosity.SUMMARISED ? null : content, List.of(), false));
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    private static Method label(EventHubsOperationInfo op, TrackingVerbosity ev) {
        return Method.of(EventHubsOperationClassifier.getDiagramLabel(op, ev));
    }

    private static URI buildUri(EventHubsOperationInfo op, TrackingVerbosity ev) {
        String hub = op.eventHubName() != null ? op.eventHubName() : "unknown";
        boolean withPartition = op.partitionId() != null
            && (ev == TrackingVerbosity.RAW || ev == TrackingVerbosity.DETAILED);
        return withPartition
            ? URI.create("eventhubs:///" + hub + "/" + op.partitionId())
            : URI.create("eventhubs:///" + hub);
    }
}
