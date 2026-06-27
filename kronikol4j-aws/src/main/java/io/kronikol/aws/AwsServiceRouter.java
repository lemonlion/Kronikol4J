package io.kronikol.aws;

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

    /** A service-agnostic classification result: the service, the diagram label, and the target resource. */
    public record AwsClassification(AwsService service, String label, String resource) {
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
                    S3OperationClassifier.getDiagramLabel(info, verbosity), info.bucketName());
            }
            case SQS -> {
                SqsOperationInfo info = SqsOperationClassifier.classify(xAmzTarget, uri, body);
                yield new AwsClassification(service,
                    SqsOperationClassifier.getDiagramLabel(info, verbosity), info.queueName());
            }
            case SNS -> {
                SnsOperationInfo info = SnsOperationClassifier.classify(xAmzTarget, uri, body);
                yield new AwsClassification(service,
                    SnsOperationClassifier.getDiagramLabel(info, verbosity), info.topicName());
            }
            case DYNAMODB -> {
                DynamoDbOperationInfo info = DynamoDbOperationClassifier.classify(xAmzTarget, body);
                yield new AwsClassification(service,
                    DynamoDbOperationClassifier.getDiagramLabel(info, verbosity), info.tableName());
            }
        };
    }
}
