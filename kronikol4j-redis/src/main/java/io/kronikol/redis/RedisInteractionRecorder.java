package io.kronikol.redis;

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
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Builds the request/response {@link RequestResponseLog} pair for a Redis command, with full parity to the
 * .NET {@code RedisTracker} ({@code LogRedisRequest}/{@code LogRedisResponse}). Two-phase: the request label
 * never carries hit/miss (only known once the response arrives); the response label does. This is the
 * reusable core a Lettuce/Jedis command hook delegates to.
 */
public final class RedisInteractionRecorder {

    private final RedisTrackerOptions options;
    private final String endpoint;

    public RedisInteractionRecorder(RedisTrackerOptions options) {
        this(options, "localhost");
    }

    public RedisInteractionRecorder(RedisTrackerOptions options, String endpoint) {
        this.options = options;
        this.endpoint = endpoint;
    }

    /** Correlation token shared by a request and its later response. */
    public record Correlation(UUID traceId, UUID requestResponseId) {
    }

    /** Emits the request half (no hit/miss yet); returns the correlation token, or empty when not tracked. */
    public Optional<Correlation> logRequest(String command, String key, int db, String content) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return Optional.empty();
        }
        TrackingVerbosity ev = effectiveVerbosity();
        RedisOperationInfo op = RedisOperationClassifier.classify(command, false, key, db);
        if (ev == TrackingVerbosity.SUMMARISED && op.operation() == RedisOperation.OTHER) {
            return Optional.empty();
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return Optional.empty();
        }

        UUID traceId = options.ids().newId();
        UUID requestResponseId = options.ids().newId();
        TestPhase phase = TestPhaseContext.current();

        // Request label never includes hit/miss.
        RedisOperationInfo requestOp = new RedisOperationInfo(op.operation(), RedisCacheResult.NONE, key, db);

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(methodFor(command, requestOp, ev))
            .content(ev == TrackingVerbosity.SUMMARISED ? null : content)
            .uri(buildUri(key, db, ev)).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .trackingIgnore(false).dependencyCategory(DependencyCategories.REDIS).phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> variant(command, requestOp, op, key, db, content, v));

        RequestResponseLogger.log(log);
        return Optional.of(new Correlation(traceId, requestResponseId));
    }

    /** Emits the response half, deriving hit/miss from {@code hasResult}. */
    public void logResponse(String command, String key, int db, boolean hasResult,
                            Correlation correlation, String content) {
        if (correlation == null
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        TrackingVerbosity ev = effectiveVerbosity();
        RedisOperationInfo op = RedisOperationClassifier.classify(command, hasResult, key, db);
        if (ev == TrackingVerbosity.SUMMARISED && op.operation() == RedisOperation.OTHER) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLog log = RequestResponseLog.builder()
            .testInfo(who).method(methodFor(command, op, ev))
            .content(ev == TrackingVerbosity.SUMMARISED ? null : content)
            .uri(buildUri(key, db, ev)).headers(List.of())
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE)
            .traceId(correlation.traceId()).requestResponseId(correlation.requestResponseId())
            .trackingIgnore(false).statusCode(StatusCode.of("OK"))
            .dependencyCategory(DependencyCategories.REDIS).phase(phase).build();

        PhaseVariantExtensions.attachVariants(log, options.verbosity(),
            options.setupVerbosity(), options.actionVerbosity(),
            v -> variant(command, op, op, key, db, content, v));

        RequestResponseLogger.log(log);
    }

    private TrackingVerbosity effectiveVerbosity() {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    /** The method/label for a half: raw uses the upper-cased command; otherwise the classified label. */
    private Method methodFor(String command, RedisOperationInfo labelOp, TrackingVerbosity ev) {
        if (ev == TrackingVerbosity.RAW) {
            return Method.of(command.toUpperCase(Locale.ROOT));
        }
        String label = RedisOperationClassifier.getDiagramLabel(labelOp, ev);
        return Method.of(label != null ? label : labelOp.operation().displayName());
    }

    /**
     * Builds a phase variant. {@code labelOp} drives the label (the hit/miss-free request op or the response
     * op); {@code skipOp} drives the Summarised/Other skip decision (always the originally-classified op).
     */
    private PhaseVariant variant(String command, RedisOperationInfo labelOp, RedisOperationInfo skipOp,
                                 String key, int db, String content, TrackingVerbosity v) {
        boolean skip = v == TrackingVerbosity.SUMMARISED && skipOp.operation() == RedisOperation.OTHER;
        return new PhaseVariant(methodFor(command, labelOp, v), buildUri(key, db, v),
            v == TrackingVerbosity.SUMMARISED ? null : content, List.of(), skip);
    }

    private URI buildUri(String key, int db, TrackingVerbosity ev) {
        return switch (ev) {
            case RAW -> key != null
                ? URI.create("redis://" + endpoint + "/" + db + "/" + key)
                : URI.create("redis://" + endpoint + "/" + db);
            case DETAILED -> key != null
                ? URI.create("redis://db" + db + "/" + key)
                : URI.create("redis://db" + db + "/");
            default -> URI.create("redis://db" + db + "/"); // Summarised
        };
    }
}
