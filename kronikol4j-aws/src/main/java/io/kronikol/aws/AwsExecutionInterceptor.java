package io.kronikol.aws;

import io.kronikol.aws.AwsServiceRouter.AwsClassification;
import io.kronikol.aws.AwsTracking.AwsTrackingOptions;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.tracking.Interactions;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.http.SdkHttpRequest;

/**
 * An AWS SDK v2 {@link ExecutionInterceptor} that auto-captures S3 / DynamoDB / SQS / SNS calls as tracked
 * interactions — the Java analog of the .NET per-service {@code *TrackingMessageHandler}s. Attach it to any
 * AWS SDK v2 client:
 *
 * <pre>{@code S3Client.builder()
 *         .overrideConfiguration(o -> o.addExecutionInterceptor(
 *             new AwsExecutionInterceptor(AwsTrackingOptions.forService("Aws").withTestInfoFetcher(...))))
 *         .build();}</pre>
 *
 * <p>After each execution it reads the marshalled {@link SdkHttpRequest} (method, URI, headers — incl.
 * {@code X-Amz-Target} / {@code x-amz-copy-source}) and request body, detects the service from the endpoint
 * host, and dispatches to {@link AwsServiceRouter} to classify and emit the pair: the classifier's label and
 * the per-service clean URI ({@code s3://bucket/key}, {@code sqs:///queue}, {@code sns:///topic},
 * {@code dynamodb:///table}) at Detailed, the raw method + request URI at Raw, with the real response status
 * and the per-service dependency category (S3 / MessageQueue / DynamoDB). Unrecognised operations are skipped
 * in Summarised mode; phase suppression and identity resolution follow the shared contracts. The AWS SDK is
 * {@code compileOnly}.
 */
public final class AwsExecutionInterceptor implements ExecutionInterceptor {

    private final AwsTrackingOptions options;

    public AwsExecutionInterceptor(AwsTrackingOptions options) {
        this.options = options;
    }

    @Override
    public void afterExecution(Context.AfterExecution context, ExecutionAttributes executionAttributes) {
        SdkHttpRequest httpRequest = context.httpRequest();
        int statusCode = context.httpResponse().statusCode();
        String requestBody = context.requestBody()
            .map(rb -> readStream(rb.contentStreamProvider().newStream()))
            .orElse(null);
        // The response stream is already consumed/unmarshalled by the SDK at this point, so it is not re-read
        // (AWS classification keys off the request, not the response body).
        track(httpRequest, statusCode, requestBody, null);
    }

    /**
     * The pure recording core: classify {@code httpRequest} + emit the pair. Package-private so it can be
     * unit-tested with a hand-built {@code SdkHttpFullRequest} (no live AWS / no {@code InterceptorContext}).
     */
    void track(SdkHttpRequest httpRequest, int statusCode, String requestBody, String responseBody) {
        if (!PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return;
        }
        URI uri = httpRequest.getUri();
        AwsService service = AwsServiceRouter.detectService(uri);
        if (service == null) {
            return; // not a recognised AWS service endpoint
        }
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return;
        }
        TrackingVerbosity verbosity = PhaseConfiguration.effectiveVerbosity(
            options.verbosity(), options.setupVerbosity(), options.actionVerbosity());

        String httpMethod = httpRequest.method().name();
        String xAmzTarget = httpRequest.firstMatchingHeader("X-Amz-Target").orElse(null);
        boolean hasCopySource = httpRequest.firstMatchingHeader("x-amz-copy-source").isPresent();

        AwsClassification c = AwsServiceRouter.classify(
            service, httpMethod, uri, xAmzTarget, hasCopySource, requestBody, verbosity);
        if (verbosity == TrackingVerbosity.SUMMARISED && c.isOther()) {
            return; // unrecognised operations are skipped in Summarised mode (matches .NET)
        }

        boolean raw = verbosity == TrackingVerbosity.RAW;
        Method method = raw ? Method.of(httpMethod) : Method.of(c.label());
        URI emitUri = raw ? uri : c.cleanUri();
        String requestContent = verbosity == TrackingVerbosity.SUMMARISED ? null : requestBody;
        String responseContent = verbosity == TrackingVerbosity.SUMMARISED ? null : responseBody;

        Interactions.recordPair(who, options.serviceName(), options.callerName(),
            c.category(), method, emitUri, requestContent, StatusCode.of(statusCode), responseContent);
    }

    private static String readStream(InputStream stream) {
        if (stream == null) {
            return null;
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null; // unreadable body — capture nothing rather than fail
        }
    }
}
