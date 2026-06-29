package io.kronikol.aws;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.aws.AwsTracking.AwsTrackingOptions;
import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.StatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * End-to-end cross-runtime capture parity for AWS SQS: drives the <em>real {@link AwsExecutionInterceptor}</em>
 * on an AWS SDK v2 {@link SqsClient} against a live LocalStack SQS (Testcontainers) and diffs the emitted
 * {@link RequestResponseLog}s against the captured output of the <em>real .NET {@code SqsTrackingMessageHandler}</em>
 * (fixture {@code sqs-interactions.txt}, harness {@code KRON_SQS_E2E=1}). Both SDKs use the AWS JSON protocol
 * ({@code X-Amz-Target: AmazonSQS.*}), so the classification matches.
 *
 * <p><strong>Result:</strong> byte-identical on type, the {@code SendMessage/ReceiveMessage} label, the
 * host-less {@code sqs:///queue} clean URI, the response-half status (200), and the request content. The
 * divergence pinned (not hidden) is the <em>response</em> body — .NET reads it in the {@code DelegatingHandler},
 * the Java {@code ExecutionInterceptor} runs after the SDK consumed the response stream, so it captures none.
 * The same interceptor-hook limitation flagged for Elasticsearch / S3-GetObject. (CreateQueue is untracked
 * setup; the Java side targets {@code sqs.localhost.localstack.cloud} so its host-based {@code detectService}
 * routes to SQS.)
 */
class SqsInteractionParityTest {

    private static GenericContainer<?> localstack;
    private static SqsClient sqs;

    @BeforeAll
    static void startLocalStack() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-LocalStack SQS parity test");
        localstack = new GenericContainer<>(DockerImageName.parse("localstack/localstack:3"))
            .withExposedPorts(4566)
            .withEnv("SERVICES", "sqs")
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
        localstack.start();
        URI endpoint = URI.create("http://sqs.localhost.localstack.cloud:" + localstack.getMappedPort(4566));
        sqs = SqsClient.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .overrideConfiguration(o -> o.addExecutionInterceptor(new AwsExecutionInterceptor(
                AwsTrackingOptions.forService("Aws").withTestInfoFetcher(() -> new TestInfo("MyTest", "t1")))))
            .build();
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (sqs != null) {
            sqs.close();
        }
        if (localstack != null) {
            localstack.stop();
        }
    }

    @Test
    void javaSqsCaptureMatchesDotNetOnTheClassificationFields() throws IOException {
        String queueUrl;
        try {
            queueUrl = sqs.createQueue(b -> b.queueName("orders-queue")).queueUrl(); // untracked setup
        } catch (software.amazon.awssdk.core.exception.SdkException unreachable) {
            Assumptions.abort("LocalStack SQS not reachable via sqs.localhost.localstack.cloud: " + unreachable);
            return;
        }

        RequestResponseLogger.clear();
        sqs.sendMessage(b -> b.queueUrl(queueUrl).messageBody("hello"));
        sqs.receiveMessage(b -> b.queueUrl(queueUrl).maxNumberOfMessages(1).waitTimeSeconds(2));

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/sqs-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[0] + " " + g[1] + ")";
            // (1) type | label | clean URI | status are byte-identical on every line (the classification parity).
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
            // (2) Request content matches (both capture the JSON request body); response content diverges (3).
            if (g[0].equals("Request")) {
                assertThat(a[3]).as(where + " request content").isEqualTo(g[3]);
            }
        }

        // (3) The divergence: the Send/Receive RESPONSE body. .NET captures it (<body>); the Java
        //     ExecutionInterceptor runs after the SDK consumed the response stream, so it captures none.
        //     Inherent interceptor-hook limitation (flagged like ES / S3-GetObject), pinned not hidden.
        for (int i = 0; i < golden.size(); i++) {
            if (golden.get(i)[0].equals("Response")) {
                assertThat(actual.get(i)[3]).as("line " + i + " java response content").isEqualTo("~null~");
                assertThat(golden.get(i)[3]).as("line " + i + " .NET response content").isEqualTo("<body>");
            }
        }
    }

    private static List<String[]> project(List<RequestResponseLog> logs) {
        List<String[]> out = new ArrayList<>();
        for (RequestResponseLog l : logs) {
            String content = (l.content() == null || l.content().isEmpty()) ? "~null~" : "<body>";
            out.add(new String[] {
                l.type().toString().equals("REQUEST") ? "Request" : "Response",
                l.method() == null ? "~null~" : l.method().value(),
                l.uri() == null ? "~null~" : l.uri().toString(),
                content,
                l.statusCode() == null ? "~null~" : statusText(l.statusCode())});
        }
        return out;
    }

    private static String statusText(StatusCode s) {
        return s instanceof StatusCode.Http h ? String.valueOf(h.code()) : ((StatusCode.Custom) s).value();
    }

    private static List<String[]> parse(String golden) {
        List<String[]> out = new ArrayList<>();
        for (String line : golden.split("\n")) {
            if (!line.isEmpty()) {
                out.add(line.split("\\|", -1));
            }
        }
        return out;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream in = SqsInteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
