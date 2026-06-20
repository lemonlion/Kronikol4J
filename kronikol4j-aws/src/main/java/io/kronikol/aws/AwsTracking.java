package io.kronikol.aws;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingDefaults;
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
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.S3, verb(operation), S3_URI, bucket + "/" + key,
            StatusCode.of("OK"), null);
    }

    /** Records a DynamoDB operation, e.g. {@code dynamoDb(opts, "PutItem", "orders", "{...}")}. */
    public static void dynamoDb(AwsTrackingOptions options, String operation, String table, String item) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.DATABASE, verb(operation), DDB_URI,
            table + ": " + (item == null ? "" : item), StatusCode.of("OK"), null);
    }

    /** Records sending a message to an SQS queue (fire-and-forget event). */
    public static void sqs(AwsTrackingOptions options, String queue, String message) {
        event(options, "SEND", queue, message);
    }

    /** Records publishing to an SNS topic (fire-and-forget event). */
    public static void sns(AwsTrackingOptions options, String topic, String message) {
        event(options, "PUBLISH", topic, message);
    }

    private static void event(AwsTrackingOptions options, String verb, String destination, String message) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        String content = "destination: " + destination + "\n" + (message == null ? "" : message);
        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            DependencyCategories.MESSAGE_QUEUE, Method.of(verb), MSG_URI, null, content,
            StatusCode.of("Sent"), null, RequestResponseMetaType.EVENT);
    }

    private static Method verb(String operation) {
        return Method.of(operation == null ? "AWS" : operation.toUpperCase(Locale.ROOT));
    }

    /** Configuration for AWS tracking. */
    public record AwsTrackingOptions(String serviceName, String callerName,
                                     Supplier<TestInfo> testInfoFetcher) {
        public static AwsTrackingOptions forService(String serviceName) {
            return new AwsTrackingOptions(serviceName, TrackingDefaults.CALLER_NAME, null);
        }
    }
}
