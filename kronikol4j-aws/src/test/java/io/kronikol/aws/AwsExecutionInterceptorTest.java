package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.aws.AwsTracking.AwsTrackingOptions;
import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;

/**
 * Drives the {@link AwsExecutionInterceptor}'s recording core ({@code track}) with hand-built
 * {@link SdkHttpFullRequest}s (no live AWS, no {@code InterceptorContext}) to verify the service routing,
 * per-service clean URIs + categories, verbosity behaviour, and the skip gates — the .NET per-service
 * {@code *TrackingMessageHandler} behaviour.
 */
class AwsExecutionInterceptorTest {

    @BeforeEach
    @AfterEach
    void clear() {
        RequestResponseLogger.clear();
    }

    private static AwsExecutionInterceptor interceptor() {
        return interceptor(AwsTrackingOptions.forService("Aws")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1")));
    }

    private static AwsExecutionInterceptor interceptor(AwsTrackingOptions options) {
        return new AwsExecutionInterceptor(options);
    }

    private static SdkHttpFullRequest request(String method, String uri, String target, String copySource) {
        SdkHttpFullRequest.Builder b = SdkHttpFullRequest.builder()
            .method(SdkHttpMethod.valueOf(method)).uri(URI.create(uri));
        if (target != null) {
            b.putHeader("X-Amz-Target", target);
        }
        if (copySource != null) {
            b.putHeader("x-amz-copy-source", copySource);
        }
        return b.build();
    }

    @Test
    void dynamoDbPutItemIsClassifiedAndRecorded() {
        interceptor().track(
            request("POST", "https://dynamodb.us-east-1.amazonaws.com/", "DynamoDB_20120810.PutItem", null),
            200, "{\"TableName\":\"orders\",\"Item\":{}}", null);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("PutItem");
        assertThat(req.uri().toString()).isEqualTo("dynamodb:///orders");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.DYNAMO_DB);
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(200));
    }

    @Test
    void s3PutObjectUsesHostFormUriWithKey() {
        interceptor().track(
            request("PUT", "https://my-bucket.s3.us-east-1.amazonaws.com/photo.jpg", null, null),
            200, null, null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("PutObject");
        assertThat(req.uri().toString()).isEqualTo("s3://my-bucket/photo.jpg"); // S3 host-form incl. key
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.S3);
    }

    @Test
    void sqsSendMessageUsesSchemeUriAndMessageQueueCategory() {
        interceptor().track(
            request("POST", "https://sqs.us-east-1.amazonaws.com/", "AmazonSQS.SendMessage", null),
            200, "{\"QueueUrl\":\"https://sqs.us-east-1.amazonaws.com/123456789012/orders-queue\"}", null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("SendMessage");
        assertThat(req.uri().toString()).isEqualTo("sqs:///orders-queue");
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
    }

    @Test
    void eventBridgePutEventsUsesEventbridgeHostUri() {
        interceptor().track(
            request("POST", "https://events.us-east-1.amazonaws.com/", "AWSEvents.PutEvents", null),
            200, "{\"Entries\":[{\"EventBusName\":\"orders\",\"DetailType\":\"OrderPlaced\",\"Source\":\"shop\"}]}",
            null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).contains("PutEvents"); // classifier label
        assertThat(req.uri().toString()).isEqualTo("eventbridge://orders/"); // host-form, bus from body
        assertThat(req.dependencyCategory()).isEqualTo(DependencyCategories.MESSAGE_QUEUE);
    }

    @Test
    void rawVerbosityUsesRawMethodAndRequestUri() {
        var options = AwsTrackingOptions.forService("Aws")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .withVerbosity(TrackingVerbosity.RAW);
        interceptor(options).track(
            request("POST", "https://dynamodb.us-east-1.amazonaws.com/", "DynamoDB_20120810.PutItem", null),
            200, "{\"TableName\":\"orders\"}", null);

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("POST"); // Raw = the HTTP method
        assertThat(req.uri().toString()).isEqualTo("https://dynamodb.us-east-1.amazonaws.com/"); // raw URI
    }

    @Test
    void summarisedSkipsUnrecognisedOperations() {
        var options = AwsTrackingOptions.forService("Aws")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .withVerbosity(TrackingVerbosity.SUMMARISED);
        interceptor(options).track(
            request("POST", "https://dynamodb.us-east-1.amazonaws.com/", "DynamoDB_20120810.Frobnicate", null),
            200, "{}", null);

        assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // Other + Summarised → skipped
    }

    @Test
    void nonAwsHostIsNotRecorded() {
        interceptor().track(request("GET", "https://example.com/api", null, null), 200, null, null);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void noTestContextSkipsRecording() {
        var options = AwsTrackingOptions.forService("Aws"); // no testInfoFetcher → null identity
        interceptor(options).track(
            request("POST", "https://dynamodb.us-east-1.amazonaws.com/", "DynamoDB_20120810.PutItem", null),
            200, "{\"TableName\":\"orders\"}", null);
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }
}
