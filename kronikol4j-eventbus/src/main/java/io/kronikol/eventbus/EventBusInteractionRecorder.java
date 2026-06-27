package io.kronikol.eventbus;

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
import java.util.UUID;

/**
 * Records message-bus operations as event-styled diagram interactions, with full parity to the .NET
 * {@code MassTransitTracker}. Send/Publish are outgoing (caller → bus); Consume is incoming (bus → caller,
 * with the participants swapped so the arrow points into the test); faults emit a {@code "Fault"} response.
 * Both halves are {@link RequestResponseMetaType#EVENT} on the {@code MessageQueue} category. This is the
 * reusable core a Spring {@code ApplicationEvent} / Axon binding delegates to.
 */
public final class EventBusInteractionRecorder {

    private final EventBusTrackerOptions options;

    public EventBusInteractionRecorder(EventBusTrackerOptions options) {
        this.options = options;
    }

    public void logSend(EventBusOperationInfo op, Object message) {
        if (options.trackSend()) {
            logOutgoing(op, message);
        }
    }

    public void logPublish(EventBusOperationInfo op, Object message) {
        if (options.trackPublish()) {
            logOutgoing(op, message);
        }
    }

    public void logConsume(EventBusOperationInfo op, Object message) {
        if (options.trackConsume()) {
            logIncoming(op, message);
        }
    }

    public void logSendFault(EventBusOperationInfo op, Throwable exception) {
        if (options.logFaults()) {
            logFault(op, exception, true);
        }
    }

    public void logPublishFault(EventBusOperationInfo op, Throwable exception) {
        if (options.logFaults()) {
            logFault(op, exception, true);
        }
    }

    public void logConsumeFault(EventBusOperationInfo op, Throwable exception) {
        if (options.logFaults()) {
            logFault(op, exception, false);
        }
    }

    private void logOutgoing(EventBusOperationInfo op, Object message) {
        emitPair(op, message, options.serviceName(), options.callerName(), false, null);
    }

    private void logIncoming(EventBusOperationInfo op, Object message) {
        // Consume is incoming: swap caller/service so the arrow points into the test.
        emitPair(op, message, options.callerName(), options.serviceName(), false, null);
    }

    private void logFault(EventBusOperationInfo op, Throwable exception, boolean outgoing) {
        String svc = outgoing ? options.serviceName() : options.callerName();
        String caller = outgoing ? options.callerName() : options.serviceName();
        emitPair(op, null, svc, caller, true, exception == null ? null : exception.getMessage());
    }

    /**
     * Emits the request+response pair (both event-styled). On the fault path the request body is the
     * exception message ({@code faultContent}) and the response carries a {@code "Fault"} status; otherwise
     * the message is serialized per the options and the response is {@code "OK"}.
     */
    private void emitPair(EventBusOperationInfo op, Object message, String serviceName, String callerName,
                          boolean fault, String faultContent) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        URI uri = EventBusOperationClassifier.buildUri(op, ev);
        Method label = Method.of(EventBusOperationClassifier.getDiagramLabel(op, ev));
        String body = fault ? faultContent : bodyFor(message, ev);
        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog request = RequestResponseLog.builder()
            .testInfo(who).method(label).content(body).uri(uri).headers(List.of())
            .serviceName(serviceName).callerName(callerName)
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(RequestResponseMetaType.EVENT)
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();
        PhaseVariantExtensions.attachVariants(request, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(EventBusOperationClassifier.getDiagramLabel(op, v)),
                EventBusOperationClassifier.buildUri(op, v),
                fault ? faultContent : bodyFor(message, v), List.of(), false));
        RequestResponseLogger.log(request);

        RequestResponseLog response = RequestResponseLog.builder()
            .testInfo(who).method(label).content(null).uri(uri).headers(List.of())
            .serviceName(serviceName).callerName(callerName)
            .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).statusCode(StatusCode.of(fault ? "Fault" : "OK"))
            .metaType(RequestResponseMetaType.EVENT)
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();
        PhaseVariantExtensions.attachVariants(response, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(EventBusOperationClassifier.getDiagramLabel(op, v)),
                EventBusOperationClassifier.buildUri(op, v), null, List.of(), false));
        RequestResponseLogger.log(response);
    }

    private String bodyFor(Object message, TrackingVerbosity ev) {
        if (!options.logMessageBody() || ev == TrackingVerbosity.SUMMARISED || message == null) {
            return null;
        }
        return options.payloadSerializer().apply(message);
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }
}
