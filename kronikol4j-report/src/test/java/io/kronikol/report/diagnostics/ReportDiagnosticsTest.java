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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Byte-parity for {@link ReportDiagnostics} against the real .NET {@code ReportDiagnostics.Analyse}
 * (the newline-joined warnings). The runtime-store warnings are empty in the harness, leaving the
 * deterministic warnings (log count, unpaired, orphaned, 0-spans).
 */
class ReportDiagnosticsTest {

    @Test
    void analyse_deterministicWarnings_matchDotNet() {
        List<RequestResponseLog> logs = List.of(
            log("Checkout succeeds", "s1", "OrderService", Method.Http.POST, RequestResponseType.REQUEST, rr(1)),
            log("Checkout succeeds", "s1", "OrderService", Method.Http.POST, RequestResponseType.RESPONSE, rr(1)),
            log("Checkout succeeds", "s1", "PaymentService", Method.Http.POST, RequestResponseType.REQUEST, rr(2)),
            log("Ghost", "orphan1", "GhostService", Method.Http.GET, RequestResponseType.REQUEST, rr(3)));
        Feature feature = new Feature("Checkout", List.of(
            Scenario.builder("Checkout succeeds", "s1", ExecutionStatus.PASSED).build()));

        List<String> warnings = ReportDiagnostics.analyse(logs, List.of(feature));

        assertThat(String.join("\n", warnings)).isEqualTo(readFixture("report-diagnostics.txt"));
    }

    @Test
    void analyse_emptyInputs_yieldNoWarnings() {
        assertThat(ReportDiagnostics.analyse(List.of(), List.of())).isEmpty();
    }

    private static RequestResponseLog log(String testName, String testId, String service, Method method,
                                          RequestResponseType type, UUID rrid) {
        return RequestResponseLog.builder()
            .testName(testName).testId(testId).method(method).uri(URI.create("http://svc/x"))
            .serviceName(service).callerName("Test").type(type)
            .traceId(UUID.randomUUID()).requestResponseId(rrid).build();
    }

    private static UUID rr(int n) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-0000000001%02x", n));
    }

    private static String readFixture(String name) {
        try (InputStream in = ReportDiagnosticsTest.class.getResourceAsStream("/parity/" + name)) {
            assertThat(in).as("fixture /parity/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
