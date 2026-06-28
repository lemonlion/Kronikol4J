package io.kronikol.azure;

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
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Records Azure Service Bus operations as event-styled diagram interactions — the Java analog of the .NET
 * {@code ServiceBusTracker}. Service Bus is AMQP (not HTTP), so the {@code TrackingServiceBusSender}/
 * {@code TrackingServiceBusReceiver} client wrappers delegate here rather than an HTTP pipeline policy.
 *
 * <p>Each operation emits a request + response pair sharing one trace/request-response id, both on the
 * {@code ServiceBus} category with the classifier's label and the {@code servicebus://<queue>[/<sub>]} URI;
 * send/receive/schedule/peek are {@link RequestResponseMetaType#EVENT}-styled (the rest plain). Mirroring
 * .NET, the response carries no HTTP status (events have none); the request body / response (error) text are
 * dropped at Summarised verbosity. Honours phase suppression + per-phase verbosity + variants.
 */
public final class ServiceBusInteractionRecorder {

    private final ServiceBusTrackerOptions options;

    public ServiceBusInteractionRecorder(ServiceBusTrackerOptions options) {
        this.options = options;
    }

    /**
     * Emits the pair for {@code op}: the request half carries {@code requestContent} (the message body), the
     * response half {@code responseContent} (an error message, or {@code null} on success).
     */
    public void record(ServiceBusOperationInfo op, String requestContent, String responseContent) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        Method label = Method.of(ServiceBusOperationClassifier.getDiagramLabel(op, ev));
        URI uri = buildUri(op, ev);
        RequestResponseMetaType metaType = isEventStyled(op.operation())
            ? RequestResponseMetaType.EVENT : RequestResponseMetaType.DEFAULT;
        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog request = RequestResponseLog.builder()
            .testInfo(who).method(label).content(ev == TrackingVerbosity.SUMMARISED ? null : requestContent)
            .uri(uri).headers(List.of()).serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(metaType)
            .dependencyCategory(DependencyCategories.SERVICE_BUS).phase(phase).build();
        PhaseVariantExtensions.attachVariants(request, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(ServiceBusOperationClassifier.getDiagramLabel(op, v)),
                buildUri(op, v), v == TrackingVerbosity.SUMMARISED ? null : requestContent, List.of(), false));
        RequestResponseLogger.log(request);

        RequestResponseLog response = RequestResponseLog.builder()
            .testInfo(who).method(label).content(ev == TrackingVerbosity.SUMMARISED ? null : responseContent)
            .uri(uri).headers(List.of()).serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(metaType)
            .dependencyCategory(DependencyCategories.SERVICE_BUS).phase(phase).build();
        PhaseVariantExtensions.attachVariants(response, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(ServiceBusOperationClassifier.getDiagramLabel(op, v)),
                buildUri(op, v), v == TrackingVerbosity.SUMMARISED ? null : responseContent, List.of(), false));
        RequestResponseLogger.log(response);
    }

    private static boolean isEventStyled(ServiceBusOperation op) {
        return switch (op) {
            case SEND, SEND_BATCH, RECEIVE, RECEIVE_BATCH, SCHEDULE, PEEK -> true;
            default -> false;
        };
    }

    /** {@code servicebus://<queue>[/<sub>]} (Detailed/Raw) or {@code servicebus://<queue>/} (Summarised). */
    static URI buildUri(ServiceBusOperationInfo op, TrackingVerbosity verbosity) {
        String queue = op.queueOrTopicName() != null ? op.queueOrTopicName() : "unknown";
        String sub = op.subscriptionName();
        if (verbosity == TrackingVerbosity.SUMMARISED) {
            return URI.create("servicebus://" + queue + "/");
        }
        return sub != null ? URI.create("servicebus://" + queue + "/" + sub)
            : URI.create("servicebus://" + queue);
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }
}
