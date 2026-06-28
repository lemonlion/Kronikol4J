package io.kronikol.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.PendingRequestResponseLogs;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the TrackingProxy enhancements: configurable URI scheme, deferred mode, id seam, serializer. */
class TrackingProxyEnhancementsTest {

    interface Calculator {
        int add(int a, int b);
    }

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
        PendingRequestResponseLogs.clear();
    }

    private static ProxyOptions baseOptions() {
        return ProxyOptions.forService("Calc")
            .withTestInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .withIds(IdGenerator.seeded(1));
    }

    @Test
    void configurableUriScheme() {
        Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum,
            baseOptions().withUriScheme("rpc://svc"));
        calc.add(2, 3);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).uri().toString()).isEqualTo("rpc://svc/Calculator/add");
    }

    @Test
    void deferredModeEnqueuesAndFlushesLater() {
        // No test identity fetcher: deferred mode still captures.
        Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum,
            ProxyOptions.forService("Calc").withTestInfoFetcher(() -> null)
                .withLogMode(TrackingLogMode.DEFERRED));

        int result = calc.add(4, 5);
        assertThat(result).isEqualTo(9);

        // Nothing logged yet; it's pending.
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
        assertThat(PendingRequestResponseLogs.count()).isEqualTo(1);

        // Flush once identity is known.
        PendingRequestResponseLogs.flushAll("MyTest", "id-1", IdGenerator.seeded(1));

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        assertThat(logs.get(0).type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(logs.get(0).serviceName()).isEqualTo("Calc");
        assertThat(logs.get(0).uri().toString()).isEqualTo("proxy://local/Calculator/add");
        assertThat(logs.get(1).type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(logs.get(1).statusCode()).isEqualTo(StatusCode.of(200));
        assertThat(logs.get(0).testName()).isEqualTo("MyTest");
    }

    @Test
    void idSeamMakesCorrelationDeterministic() {
        Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum, baseOptions());
        calc.add(1, 1);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        // request + response share the request-response id; trace ids come from the seeded generator.
        assertThat(logs.get(0).requestResponseId()).isEqualTo(logs.get(1).requestResponseId());
        assertThat(logs.get(0).traceId()).isNotNull();
    }

    @Test
    void actionPhaseSuppressionPassesThroughUntracked() {
        Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum,
            baseOptions().withTrackDuringAction(false));
        io.kronikol.core.context.TestPhaseContext.set(io.kronikol.core.tracking.TestPhase.ACTION);
        try {
            assertThat(calc.add(2, 3)).isEqualTo(5); // call still runs (pass-through)
            assertThat(RequestResponseLogger.getAllLogs()).isEmpty(); // but untracked in the Action phase
        } finally {
            io.kronikol.core.context.TestPhaseContext.reset();
        }
    }

    @Test
    void customSerializerControlsContent() {
        Calculator calc = TrackingProxy.wrap(Calculator.class, Integer::sum,
            baseOptions().withSerializer(value -> "<" + value + ">"));
        calc.add(7, 8);

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs.get(1).content()).isEqualTo("<15>"); // response value via the custom serializer
    }
}
