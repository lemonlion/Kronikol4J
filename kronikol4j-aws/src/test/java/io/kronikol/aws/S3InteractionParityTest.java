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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * End-to-end cross-runtime capture parity for AWS S3: drives the <em>real {@link AwsExecutionInterceptor}</em>
 * on an AWS SDK v2 {@link S3Client} against a live LocalStack S3 (Testcontainers) and diffs the emitted
 * {@link RequestResponseLog}s against the captured output of the <em>real .NET {@code S3TrackingMessageHandler}</em>
 * driven against a live LocalStack S3 (fixture {@code s3-interactions.txt}, harness {@code KRON_S3_E2E=1}).
 *
 * <p><strong>Result:</strong> the classification is byte-identical cross-runtime — {@code type}, the
 * {@code PutObject/GetObject/DeleteObject} label, the host-less {@code s3://bucket/key} clean URI, the
 * <em>response</em>-half status (200/200/204), and the <em>request</em> content all match exactly. The one
 * divergence is pinned (not hidden): the {@code GetObject} <em>response</em> body — .NET reads it from the
 * {@code DelegatingHandler}, whereas the Java {@code ExecutionInterceptor} runs after the SDK has already
 * consumed/unmarshalled the response stream, so it captures none. An inherent interceptor-hook limitation
 * (the same one flagged for Elasticsearch), not a classifier difference. (CreateBucket is intentionally
 * untracked setup — the two SDKs marshal it differently.)
 */
class S3InteractionParityTest {

    private static GenericContainer<?> localstack;
    private static S3Client s3;

    @BeforeAll
    static void startLocalStack() {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
            "No Docker available — skipping the live-LocalStack S3 parity test");
        localstack = new GenericContainer<>(DockerImageName.parse("localstack/localstack:3"))
            .withExposedPorts(4566)
            .withEnv("SERVICES", "s3")
            .waitingFor(Wait.forHttp("/_localstack/health").forPort(4566).forStatusCode(200)
                .withStartupTimeout(Duration.ofMinutes(3)));
        localstack.start();
        // The Java adapter's single ExecutionInterceptor detects the AWS service from the endpoint HOST (it has
        // no per-service client, unlike the .NET S3-specific handler). LocalStack's s3.localhost.localstack.cloud
        // alias (→ 127.0.0.1) puts "s3" in the host so detectService routes to S3 — the mapped port still hits
        // this container. The clean URI compared against the golden is s3://bucket/key (endpoint-independent).
        URI endpoint = URI.create("http://s3.localhost.localstack.cloud:" + localstack.getMappedPort(4566));
        s3 = S3Client.builder()
            .endpointOverride(endpoint)
            .region(Region.US_EAST_1)
            .forcePathStyle(true)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
            .overrideConfiguration(o -> o.addExecutionInterceptor(new AwsExecutionInterceptor(
                AwsTrackingOptions.forService("Aws").withTestInfoFetcher(() -> new TestInfo("MyTest", "t1")))))
            .build();
    }

    @AfterAll
    static void stop() {
        RequestResponseLogger.clear(); // don't leak this test's logs into the shared static logger
        TestIdentityScope.clear();
        if (s3 != null) {
            s3.close();
        }
        if (localstack != null) {
            localstack.stop();
        }
    }

    @Test
    void javaS3CaptureMatchesDotNetOnTheClassificationFields() throws IOException {
        try {
            s3.createBucket(b -> b.bucket("orders-bucket")); // untracked setup (cleared below)
        } catch (software.amazon.awssdk.core.exception.SdkException unreachable) {
            // e.g. s3.localhost.localstack.cloud not DNS-resolvable on this box — skip rather than fail.
            Assumptions.abort("LocalStack S3 not reachable via s3.localhost.localstack.cloud: " + unreachable);
        }

        RequestResponseLogger.clear();
        s3.putObject(b -> b.bucket("orders-bucket").key("photo.jpg"), RequestBody.fromString("hello"));
        ResponseBytes<GetObjectResponse> got = s3.getObjectAsBytes(b -> b.bucket("orders-bucket").key("photo.jpg"));
        got.asByteArray(); // drain
        s3.deleteObject(b -> b.bucket("orders-bucket").key("photo.jpg"));

        List<String[]> actual = project(RequestResponseLogger.getAllLogs());
        List<String[]> golden = parse(readResource("/parity/s3-interactions.txt"));
        assertThat(actual).hasSameSizeAs(golden);

        for (int i = 0; i < golden.size(); i++) {
            String[] a = actual.get(i);
            String[] g = golden.get(i);
            String where = "line " + i + " (" + g[0] + " " + g[1] + ")";
            // (1) type | label | clean URI are byte-identical on every line (the core classification parity).
            assertThat(a[0]).as(where + " type").isEqualTo(g[0]);
            assertThat(a[1]).as(where + " label").isEqualTo(g[1]);
            assertThat(a[2]).as(where + " uri").isEqualTo(g[2]);
            // (2) status matches on every line (request halves are both ~null~; responses are 200/200/204).
            assertThat(a[4]).as(where + " status").isEqualTo(g[4]);
            // (3) content matches on every line EXCEPT the GetObject response (index 3) — see (4).
            if (i != 3) {
                assertThat(a[3]).as(where + " content").isEqualTo(g[3]);
            }
        }

        // (4) The only divergence: the GetObject response body. .NET captures it (<body>); the Java
        //     ExecutionInterceptor runs after the SDK consumed the response stream, so it captures none.
        //     Inherent interceptor-hook limitation (flagged like Elasticsearch), pinned not hidden.
        assertThat(actual.get(3)[3]).as("GetObject response content (Java interceptor)").isEqualTo("~null~");
        assertThat(golden.get(3)[3]).as("GetObject response content (.NET handler)").isEqualTo("<body>");
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
        try (InputStream in = S3InteractionParityTest.class.getResourceAsStream(path)) {
            assertThat(in).as("fixture " + path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
