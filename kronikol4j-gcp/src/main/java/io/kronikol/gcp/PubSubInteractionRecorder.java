package io.kronikol.gcp;

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
 * Records Google Cloud Pub/Sub operations as event interactions — the Java analog of the .NET
 * {@code PubSubTracker}. Pub/Sub is gRPC, so the {@code TrackingPublisher}/{@code TrackingMessageReceiver}
 * wrappers delegate here. Each operation emits a request + response pair sharing one trace/request-response
 * id on the {@code MessageQueue} category with the classifier's label and the {@code pubsub:///<name>} URI;
 * the request half is {@link RequestResponseMetaType#EVENT}-styled, the response plain (no HTTP status —
 * matching .NET). The request body / response (id or error) text are dropped at Summarised verbosity.
 */
public final class PubSubInteractionRecorder {

    private final PubSubTrackerOptions options;

    public PubSubInteractionRecorder(PubSubTrackerOptions options) {
        this.options = options;
    }

    /**
     * Emits the pair for {@code op}: the request half carries {@code requestContent} (the message body), the
     * response half {@code responseContent} (the message id, or an error message, or {@code null}).
     */
    public void record(PubSubOperationInfo op, String requestContent, String responseContent) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        Method label = Method.of(PubSubOperationClassifier.getDiagramLabel(op, ev));
        URI uri = buildUri(op, ev);
        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog request = RequestResponseLog.builder()
            .testInfo(who).method(label).content(ev == TrackingVerbosity.SUMMARISED ? null : requestContent)
            .uri(uri).headers(List.of()).serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).metaType(RequestResponseMetaType.EVENT)
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();
        PhaseVariantExtensions.attachVariants(request, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(PubSubOperationClassifier.getDiagramLabel(op, v)),
                buildUri(op, v), v == TrackingVerbosity.SUMMARISED ? null : requestContent, List.of(), false));
        RequestResponseLogger.log(request);

        RequestResponseLog response = RequestResponseLog.builder()
            .testInfo(who).method(label).content(ev == TrackingVerbosity.SUMMARISED ? null : responseContent)
            .uri(uri).headers(List.of()).serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false)
            .dependencyCategory(DependencyCategories.MESSAGE_QUEUE).phase(phase).build();
        PhaseVariantExtensions.attachVariants(response, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> new PhaseVariant(Method.of(PubSubOperationClassifier.getDiagramLabel(op, v)),
                buildUri(op, v), v == TrackingVerbosity.SUMMARISED ? null : responseContent, List.of(), false));
        RequestResponseLogger.log(response);
    }

    /** {@code pubsub:///<short name>} (Detailed/Summarised) or {@code pubsub:///<full name>} (Raw). */
    static URI buildUri(PubSubOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.RAW) {
            String full = op.topicName() != null ? op.topicName()
                : op.subscriptionName() != null ? op.subscriptionName() : "unknown";
            return URI.create("pubsub:///" + full);
        }
        String name = shortName(op.topicName());
        if (name == null) {
            name = shortName(op.subscriptionName());
        }
        return URI.create("pubsub:///" + (name != null ? name : "unknown"));
    }

    private static String shortName(String fullName) {
        if (fullName == null) {
            return null;
        }
        int slash = fullName.lastIndexOf('/');
        return slash >= 0 ? fullName.substring(slash + 1) : fullName;
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }
}
