package io.kronikol.report.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Byte-parity for {@link DiagnosticReportGenerator} against the real .NET
 * {@code DiagnosticReportGenerator} (via its public {@code Generate}). Exercises every deterministic
 * section — config dump, log summary, per-service / per-test, "unknown" breakdown (first/last seen),
 * unpaired requests, orphaned ids, no-log scenarios, activity-span count — and the {@code <}/{@code &}
 * escaping. The runtime-registry sections are empty here (as in the harness), so the full output matches.
 */
class DiagnosticReportGeneratorTest {

    @Test
    void buildHtml_deterministicSections_isByteForByteIdenticalToDotNet() {
        List<RequestResponseLog> logs = List.of(
            log("Checkout succeeds", "s1", Method.Http.POST, "http://orders/checkout", "OrderService",
                RequestResponseType.REQUEST, rr(1), ts(1, 0)),
            log("Checkout succeeds", "s1", Method.Http.POST, "http://orders/checkout", "OrderService",
                RequestResponseType.RESPONSE, rr(1), ts(1, 250)),
            log("Checkout succeeds", "s1", Method.Http.POST, "http://pay/charge?amt=5&cur=USD",
                "PaymentService", RequestResponseType.REQUEST, rr(2), ts(1, 300)),
            log("Lookup order", "s2", Method.Http.GET, "http://orders/123", "OrderService",
                RequestResponseType.REQUEST, rr(3), ts(2, 0)),
            log("Lookup order", "s2", Method.Http.GET, "http://orders/123", "OrderService",
                RequestResponseType.RESPONSE, rr(3), ts(2, 100)),
            log("BackgroundPoll", "unknown", Method.Http.GET, "http://bg/poll", "BackgroundSvc",
                RequestResponseType.REQUEST, rr(4), ts(2, 0)),
            log("BackgroundPoll", "unknown", Method.Http.GET, "http://bg/poll", "BackgroundSvc",
                RequestResponseType.REQUEST, rr(5), ts(8, 0)),
            log("Ghost & Co", "orphan1", Method.Http.GET, "http://ghost/x", "GhostService",
                RequestResponseType.REQUEST, rr(6), ts(3, 0)));
        Feature checkout = new Feature("Checkout", List.of(
            Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED).build(),
            Scenario.builder("Edge <case>", "s3", ExecutionStatus.PASSED).build()));
        Feature lookup = new Feature("Lookup", List.of(
            Scenario.builder("Lookup order", "s2", ExecutionStatus.PASSED).build()));

        String html = DiagnosticReportGenerator.buildHtml(
            logs, List.of(checkout, lookup), DiagnosticConfig.dotNetDefaults());

        assertThat(html).isEqualTo(readFixture("diagnostic-report.html"));
    }

    private static RequestResponseLog log(String testName, String testId, Method method, String uri,
                                          String service, RequestResponseType type, UUID rrid,
                                          OffsetDateTime ts) {
        RequestResponseLog l = RequestResponseLog.builder()
            .testName(testName).testId(testId).method(method).uri(URI.create(uri))
            .serviceName(service).callerName("Test").type(type)
            .traceId(UUID.randomUUID()).requestResponseId(rrid).build();
        l.timestamp(ts);
        return l;
    }

    private static UUID rr(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-0000000001%02x", n));
    }

    private static OffsetDateTime ts(int second, int millis) {
        return OffsetDateTime.of(2024, 1, 15, 10, 0, second, millis * 1_000_000, ZoneOffset.UTC);
    }

    private static String readFixture(String name) {
        try (InputStream in = DiagnosticReportGeneratorTest.class.getResourceAsStream("/parity/" + name)) {
            assertThat(in).as("fixture /parity/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
