package io.kronikol.aws;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;

/**
 * Detects which AWS service a request targets (from the endpoint host) and dispatches to the matching
 * per-service classifier, returning a unified {@link AwsClassification}. This is the glue an AWS SDK v2
 * {@code ExecutionInterceptor} uses: it has the request's method/URI/headers/body but not a per-service
 * client, so it routes here. Pure logic — no AWS SDK dependency.
 */
public final class AwsServiceRouter {

    private AwsServiceRouter() {
    }

    /**
     * A service-agnostic classification result: the service, the diagram label, the target resource, the
     * clean diagram URI (per-service form — {@code s3://bucket/key}, {@code sqs:///queue}, …), the dependency
     * category to record under, and whether the operation was unrecognised ({@code Other}).
     */
    public record AwsClassification(AwsService service, String label, String resource, URI cleanUri,
                                    String category, boolean isOther) {
    }

    /** Detects the AWS service from the request URI host, or {@code null} if it is not a recognised service. */
    public static AwsService detectService(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return null;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.contains("sqs.")) {
            return AwsService.SQS;
        }
        if (host.contains("sns.")) {
            return AwsService.SNS;
        }
        if (host.contains("dynamodb.")) {
            return AwsService.DYNAMODB;
        }
        if (host.startsWith("s3") || host.contains(".s3.") || host.contains(".s3-") || host.contains("s3.")) {
            return AwsService.S3;
        }
        return null;
    }

    /**
     * Classifies a request once the service is known. {@code xAmzTarget} is the {@code X-Amz-Target} header
     * (for SQS/SNS/DynamoDB JSON protocols); {@code hasCopySource} is the {@code x-amz-copy-source} header
     * presence (for S3). Returns {@code null} if {@code service} is {@code null}.
     */
    public static AwsClassification classify(AwsService service, String method, URI uri, String xAmzTarget,
                                             boolean hasCopySource, String body, TrackingVerbosity verbosity) {
        if (service == null) {
            return null;
        }
        return switch (service) {
            case S3 -> {
                S3OperationInfo info = S3OperationClassifier.classify(method, uri, hasCopySource);
                yield new AwsClassification(service,
                    S3OperationClassifier.getDiagramLabel(info, verbosity), info.bucketName(),
                    buildS3Uri(info, verbosity), DependencyCategories.S3, info.operation() == S3Operation.OTHER);
            }
            case SQS -> {
                SqsOperationInfo info = SqsOperationClassifier.classify(xAmzTarget, uri, body);
                yield new AwsClassification(service,
                    SqsOperationClassifier.getDiagramLabel(info, verbosity), info.queueName(),
                    schemeUri("sqs", info.queueName()), DependencyCategories.MESSAGE_QUEUE,
                    info.operation() == SqsOperation.OTHER);
            }
            case SNS -> {
                SnsOperationInfo info = SnsOperationClassifier.classify(xAmzTarget, uri, body);
                yield new AwsClassification(service,
                    SnsOperationClassifier.getDiagramLabel(info, verbosity), info.topicName(),
                    schemeUri("sns", info.topicName()), DependencyCategories.MESSAGE_QUEUE,
                    info.operation() == SnsOperation.OTHER);
            }
            case DYNAMODB -> {
                DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify(xAmzTarget, body);
                yield new AwsClassification(service,
                    DynamoDbOperationClassifier.getDiagramLabel(info, verbosity), info.tableName(),
                    schemeUri("dynamodb", info.tableName()), DependencyCategories.DYNAMO_DB,
                    info.operation() == DynamoDbOperation.OTHER);
            }
        };
    }

    /** {@code <scheme>:///} (no resource) or {@code <scheme>:///<resource>} — matches the .NET handlers. */
    private static URI schemeUri(String scheme, String resource) {
        return resource == null ? URI.create(scheme + ":///") : URI.create(scheme + ":///" + resource);
    }

    /** The S3 host-form URI: {@code s3:///} (no bucket), {@code s3://bucket/} (Summarised or no key), else
     *  {@code s3://bucket/key} — matches the .NET {@code S3TrackingMessageHandler.BuildCleanUri}. */
    private static URI buildS3Uri(S3OperationInfo info, TrackingVerbosity verbosity) {
        if (info.bucketName() == null) {
            return URI.create("s3:///");
        }
        if (verbosity == TrackingVerbosity.SUMMARISED || info.keyName() == null) {
            return URI.create("s3://" + info.bucketName() + "/");
        }
        return URI.create("s3://" + info.bucketName() + "/" + info.keyName());
    }
}
