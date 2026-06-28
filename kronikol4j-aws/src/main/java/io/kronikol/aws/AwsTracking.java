package io.kronikol.aws;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Records AWS service operations as tracked interactions. The recorders are pure (no AWS SDK on the
 * classpath); an AWS SDK {@code ExecutionInterceptor} delegates to them.
 *
 * <ul>
 *   <li>S3 → {@link DependencyCategories#S3} (database shape)</li>
 *   <li>DynamoDB → {@link DependencyCategories#DATABASE} (database shape)</li>
 *   <li>SQS / SNS → {@link DependencyCategories#MESSAGE_QUEUE} (queue shape, fire-and-forget event)</li>
 * </ul>
 */
public final class AwsTracking {

    private static final URI S3_URI = URI.create("aws://s3/");
    private static final URI DDB_URI = URI.create("aws://dynamodb/");
    private static final URI MSG_URI = URI.create("aws://messaging/");

    private AwsTracking() {
    }

    /** Records an S3 operation, e.g. {@code s3(opts, "PUT", "my-bucket", "photo.jpg")}. */
    public static void s3(AwsTrackingOptions options, String operation, String bucket, String key) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.S3, verb(operation), S3_URI, bucket + "/" + key,
            StatusCode.of("OK"), null);
    }

    /** Records a DynamoDB operation, e.g. {@code dynamoDb(opts, "PutItem", "orders", "{...}")}.
     *  At Summarised verbosity the item payload is omitted (only the table identity is kept). */
    public static void dynamoDb(AwsTrackingOptions options, String operation, String table, String item) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        String payload = effectiveVerbosity(options).includesPayload() && item != null ? item : "";
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.DATABASE, verb(operation), DDB_URI,
            table + ": " + payload, StatusCode.of("OK"), null);
    }

    /** Records sending a message to an SQS queue (fire-and-forget event). */
    public static void sqs(AwsTrackingOptions options, String queue, String message) {
        event(options, "SEND", queue, message);
    }

    /** Records publishing to an SNS topic (fire-and-forget event). */
    public static void sns(AwsTrackingOptions options, String topic, String message) {
        event(options, "PUBLISH", topic, message);
    }

    /**
     * Records an Amazon EventBridge interaction (fire-and-forget event), classifying the request from its
     * {@code X-Amz-Target} header + JSON body via {@link EventBridgeOperationClassifier} — the reusable core
     * an AWS SDK interceptor delegates to. The diagram label comes from the classifier; the URI is
     * {@code eventbridge://<bus>/} (bus defaults to {@code "default"}, matching .NET); the body is honoured per
     * the (per-phase) verbosity. Queue/event shape, like SQS/SNS.
     *
     * @param xAmzTarget the {@code X-Amz-Target} header (e.g. {@code "AWSEvents.PutEvents"})
     * @param body       the request JSON body (or a redacted form); dropped at Summarised verbosity
     */
    public static void eventBridge(AwsTrackingOptions options, String xAmzTarget, String body) {
        if (suppressedByPhase(options)) {
            return;
        }
        EventBridgeOperationInfo info = EventBridgeOperationClassifier.classify(xAmzTarget, body);
        TrackingVerbosity verbosity = effectiveVerbosity(options);
        String label = EventBridgeOperationClassifier.getDiagramLabel(info, verbosity);
        String bus = info.eventBusName() != null ? info.eventBusName() : "default";
        URI uri = URI.create("eventbridge://" + bus + "/");
        String content = verbosity.includesPayload() && body != null ? body : null;
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of(label), uri, null, content,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static void event(AwsTrackingOptions options, String verb, String destination, String message) {
        if (suppressedByPhase(options)) {
            return;
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        // Summarised omits the message payload — only the destination identity is kept.
        String payload = effectiveVerbosity(options).includesPayload() && message != null ? message : "";
        String content = "destination: " + destination + "\n" + payload;
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of(verb), MSG_URI, null, content,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static Method verb(String operation) {
        return Method.of(operation == null ? "AWS" : operation.toUpperCase(Locale.ROOT));
    }

    /** Whether the current phase suppresses tracking per the options' {@code trackDuringSetup/Action}. */
    private static boolean suppressedByPhase(AwsTrackingOptions options) {
        return !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction());
    }

    /** The verbosity for the current phase: the per-phase override when set, else the base (the .NET
     *  {@code SetupVerbosity}/{@code ActionVerbosity} resolution). */
    private static TrackingVerbosity effectiveVerbosity(AwsTrackingOptions options) {
        return PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());
    }

    /** Configuration for AWS tracking. */
    public record AwsTrackingOptions(String serviceName, String callerName,
                                     Supplier<TestInfo> testInfoFetcher, TrackingVerbosity verbosity,
                                     boolean trackDuringSetup, boolean trackDuringAction,
                                     TrackingVerbosity setupVerbosity, TrackingVerbosity actionVerbosity) {

        public AwsTrackingOptions {
            verbosity = verbosity == null ? TrackingVerbosity.DEFAULT : verbosity;
        }

        /** Three-arg shape (default verbosity, both phases tracked) — back-compatible. */
        public AwsTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher) {
            this(serviceName, callerName, testInfoFetcher, TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** Four-arg shape (both phases tracked) — back-compatible. */
        public AwsTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                  TrackingVerbosity verbosity) {
            this(serviceName, callerName, testInfoFetcher, verbosity, true, true, null, null);
        }

        /** Six-arg shape (no per-phase verbosity overrides) — back-compatible. */
        public AwsTrackingOptions(String serviceName, String callerName, Supplier<TestInfo> testInfoFetcher,
                                  TrackingVerbosity verbosity, boolean trackDuringSetup,
                                  boolean trackDuringAction) {
            this(serviceName, callerName, testInfoFetcher, verbosity, trackDuringSetup, trackDuringAction,
                null, null);
        }

        public static AwsTrackingOptions forService(String serviceName) {
            return new AwsTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null,
                TrackingVerbosity.DEFAULT, true, true, null, null);
        }

        /** A copy with the given verbosity (Summarised omits the DynamoDB item / message payloads). */
        public AwsTrackingOptions withVerbosity(TrackingVerbosity value) {
            return new AwsTrackingOptions(serviceName, callerName, testInfoFetcher, value,
                trackDuringSetup, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Setup phase (the .NET {@code TrackDuringSetup}). */
        public AwsTrackingOptions withTrackDuringSetup(boolean value) {
            return new AwsTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                value, trackDuringAction, setupVerbosity, actionVerbosity);
        }

        /** A copy that (does not) track during the Action phase (the .NET {@code TrackDuringAction}). */
        public AwsTrackingOptions withTrackDuringAction(boolean value) {
            return new AwsTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, value, setupVerbosity, actionVerbosity);
        }

        /** A copy with a Setup-phase verbosity override (the .NET {@code SetupVerbosity}; {@code null} = base). */
        public AwsTrackingOptions withSetupVerbosity(TrackingVerbosity value) {
            return new AwsTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, value, actionVerbosity);
        }

        /** A copy with an Action-phase verbosity override (the .NET {@code ActionVerbosity}; {@code null} = base). */
        public AwsTrackingOptions withActionVerbosity(TrackingVerbosity value) {
            return new AwsTrackingOptions(serviceName, callerName, testInfoFetcher, verbosity,
                trackDuringSetup, trackDuringAction, setupVerbosity, value);
        }
    }
}
