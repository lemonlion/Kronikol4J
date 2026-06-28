package io.kronikol.messaging;

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
import java.util.Map;
import java.util.UUID;

/**
 * Logs non-HTTP interactions (events, messages, commands) so they appear in the sequence diagrams alongside
 * HTTP traffic. Java port of the .NET {@code MessageTracker} — an <em>injectable</em> (non-static) tracker
 * holding per-instance options, unlike the static {@link MessageTracking} helper. Inject one instance into
 * any fake/stub that simulates publishing or consuming messages.
 *
 * <p>Exposes the distinct .NET tracking methods: {@link #trackMessageRequest}/{@link #trackMessageResponse}
 * (caller-controlled request/response timing), {@link #trackSendEvent} (event-styled fire-and-forget pair),
 * {@link #trackSendMessage} (atomic send pair with a {@code "Sent"} ack), and {@link #trackConsumeEvent}
 * (broker→consumer delivery + ack, note on the right).
 */
public final class MessageTracker implements io.kronikol.core.registry.TrackingComponent {

    private final MessageTrackerOptions options;
    private final java.util.concurrent.atomic.AtomicInteger invocations =
        new java.util.concurrent.atomic.AtomicInteger();

    public MessageTracker(MessageTrackerOptions options) {
        this.options = options;
        io.kronikol.core.registry.TrackingComponentRegistry.register(this);
    }

    @Override
    public String componentName() {
        return "MessageTracker (" + options.serviceName() + ")";
    }

    @Override
    public boolean wasInvoked() {
        return invocations.get() > 0;
    }

    @Override
    public int invocationCount() {
        return invocations.get();
    }

    /** Logs a request for a message sent to a destination; returns the correlation id (or null if skipped). */
    public UUID trackMessageRequest(String protocol, String destinationName, URI destinationUri,
                                    Object payload, boolean noteOnRight) {
        invocations.incrementAndGet();
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return null;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return null;
        }
        UUID requestResponseId = options.ids().newId();
        TrackingVerbosity ev = effectiveVerbosity();
        String content = ev == TrackingVerbosity.SUMMARISED ? null : serialize(payload);
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = baseBuilder(who, protocol, content, destinationUri, destinationName,
            RequestResponseType.REQUEST, requestResponseId, phase).build().noteOnRight(noteOnRight);

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(protocol), destinationUri,
                v == TrackingVerbosity.SUMMARISED ? null : serialize(payload), List.of(), false));

        RequestResponseLogger.log(log);
        return requestResponseId;
    }

    /** Logs the response for a previously-tracked message request. */
    public void trackMessageResponse(String protocol, String destinationName, URI destinationUri,
                                     UUID requestResponseId, Object responsePayload, String statusLabel) {
        if (requestResponseId == null
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        String content = ev == TrackingVerbosity.SUMMARISED
            ? null
            : responsePayload != null ? serialize(responsePayload) : "";
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = baseBuilder(who, protocol, content, destinationUri, destinationName,
            RequestResponseType.RESPONSE, requestResponseId, phase)
            .statusCode(StatusCode.of(statusLabel)).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(protocol), destinationUri,
                v == TrackingVerbosity.SUMMARISED ? null
                    : responsePayload != null ? serialize(responsePayload) : "",
                List.of(), false));

        RequestResponseLogger.log(log);
    }

    /** Convenience overload with the default {@code "Responded"} status label. */
    public void trackMessageResponse(String protocol, String destinationName, URI destinationUri,
                                     UUID requestResponseId, Object responsePayload) {
        trackMessageResponse(protocol, destinationName, destinationUri, requestResponseId, responsePayload,
            "Responded");
    }

    /** Logs a fire-and-forget send as an event-styled request+response pair. */
    public void trackSendEvent(String protocol, String destinationName, URI destinationUri, Object payload) {
        UUID id = trackMessageRequest(protocol, destinationName, destinationUri, orEmpty(payload), false);
        if (id != null) {
            trackMessageResponse(protocol, destinationName, destinationUri, id, null, "Responded");
        }
    }

    /** Logs an atomic send pair with a {@code "Sent"} acknowledgement; returns the correlation id or null. */
    public UUID trackSendMessage(String protocol, String destinationName, URI destinationUri, Object payload) {
        invocations.incrementAndGet();
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return null;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return null;
        }
        UUID requestResponseId = options.ids().newId();
        TrackingVerbosity ev = effectiveVerbosity();
        String content = ev == TrackingVerbosity.SUMMARISED ? null : serialize(orEmpty(payload));
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLogger.log(baseBuilder(who, protocol, content, destinationUri, destinationName,
            RequestResponseType.REQUEST, requestResponseId, phase).build());
        RequestResponseLogger.log(baseBuilder(who, protocol, null, destinationUri, destinationName,
            RequestResponseType.RESPONSE, requestResponseId, phase)
            .statusCode(StatusCode.of("Sent")).build());

        return requestResponseId;
    }

    /** Tracks consumption of an event (broker→consumer) as a delivery + ack pair, note on the right. */
    public void trackConsumeEvent(String protocol, String consumerName, URI sourceUri, Object payload,
                                  String ackLabel) {
        UUID id = trackMessageRequest(protocol, consumerName, sourceUri, orEmpty(payload), true);
        if (id != null) {
            trackMessageResponse(protocol, consumerName, sourceUri, id, null, ackLabel);
        }
    }

    /** Convenience overload with the default {@code "Ack"} label. */
    public void trackConsumeEvent(String protocol, String consumerName, URI sourceUri, Object payload) {
        trackConsumeEvent(protocol, consumerName, sourceUri, payload, "Ack");
    }

    /**
     * Logs a self-contained lifecycle event (e.g. Kafka Flush/Commit/Subscribe/transaction) as an event pair
     * carrying no body and no status — the .NET {@code KafkaTracker.LogOutgoing(op, null)} shape. {@code label}
     * is the diagram label (from the operation classifier); {@code uri} the {@code kafka://} URI.
     */
    public void trackEvent(String label, URI uri) {
        invocations.incrementAndGet();
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();
        RequestResponseLogger.log(baseBuilder(who, label, null, uri, options.serviceName(),
            RequestResponseType.REQUEST, requestResponseId, phase).build());
        RequestResponseLogger.log(baseBuilder(who, label, null, uri, options.serviceName(),
            RequestResponseType.RESPONSE, requestResponseId, phase).build());
    }

    private RequestResponseLog.Builder baseBuilder(TestInfo who, String protocol, String content,
                                                   URI uri, String destinationName, RequestResponseType type,
                                                   UUID requestResponseId, TestPhase phase) {
        return RequestResponseLog.builder()
            .testInfo(who).method(Method.of(protocol)).content(content).uri(uri).headers(List.of())
            .serviceName(destinationName).callerName(options.callerName())
            .type(type).traceId(options.ids().newId()).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(RequestResponseMetaType.EVENT)
            .dependencyCategory(options.dependencyCategory())
            .callerDependencyCategory(options.callerDependencyCategory()).phase(phase);
    }

    /** The verbosity for the current phase (per-phase override, else base) — used by the Kafka wrappers to
     *  classify labels/URIs at the same level this tracker logs content. */
    public TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    private String serialize(Object payload) {
        return options.payloadSerializer().apply(orEmpty(payload));
    }

    private static Object orEmpty(Object payload) {
        return payload != null ? payload : Map.of();
    }
}
