package io.kronikol.redis;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.support.IdGenerator;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Redis request/response log pair matches .NET {@code RedisTracker}: the request label omits
 * hit/miss while the response label includes it, with the verbosity-driven {@code redis://} URI matrix.
 */
class RedisInteractionRecorderTest {

    @AfterEach
    void cleanup() {
        RequestResponseLogger.clear();
    }

    private static RedisTrackerOptions.Builder opts() {
        return RedisTrackerOptions.builder()
            .serviceName("Cache")
            .testInfoFetcher(() -> new TestInfo("MyTest", "id-1"))
            .ids(IdGenerator.seeded(1));
    }

    @Test
    void detailedRequestHasNoHitMissResponseHasHit() {
        RedisInteractionRecorder rec = new RedisInteractionRecorder(opts().build());

        Optional<RedisInteractionRecorder.Correlation> corr = rec.logRequest("GET", "user:1", 0, "user:1");
        assertThat(corr).isPresent();
        rec.logResponse("GET", "user:1", 0, true, corr.get(), "{\"name\":\"Ada\"}");

        List<RequestResponseLog> logs = RequestResponseLogger.getAllLogs();
        assertThat(logs).hasSize(2);
        RequestResponseLog req = logs.get(0);
        RequestResponseLog res = logs.get(1);

        assertThat(req.type()).isEqualTo(RequestResponseType.REQUEST);
        assertThat(req.method().value()).isEqualTo("Get"); // request label: no hit/miss
        assertThat(req.uri().toString()).isEqualTo("redis://db0/user:1");
        assertThat(req.serviceName()).isEqualTo("Cache");

        assertThat(res.type()).isEqualTo(RequestResponseType.RESPONSE);
        assertThat(res.method().value()).isEqualTo("Get (Hit)"); // response label: hit
        assertThat(res.statusCode()).isEqualTo(StatusCode.of("OK"));
        assertThat(req.traceId()).isEqualTo(res.traceId());
    }

    @Test
    void missWhenNoResult() {
        RedisInteractionRecorder rec = new RedisInteractionRecorder(opts().build());
        var corr = rec.logRequest("GET", "absent", 0, null).orElseThrow();
        rec.logResponse("GET", "absent", 0, false, corr, null);

        assertThat(RequestResponseLogger.getAllLogs().get(1).method().value()).isEqualTo("Get (Miss)");
    }

    @Test
    void rawVerbosityUsesCommandAndEndpointUri() {
        RedisInteractionRecorder rec =
            new RedisInteractionRecorder(opts().verbosity(TrackingVerbosity.RAW).build(), "redis-host");
        rec.logRequest("get", "user:1", 2, "v");

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("GET"); // raw upper-cased command
        assertThat(req.uri().toString()).isEqualTo("redis://redis-host/2/user:1");
        assertThat(req.content()).isEqualTo("v");
    }

    @Test
    void summarisedOmitsContentAndKeyInUri() {
        RedisInteractionRecorder rec =
            new RedisInteractionRecorder(opts().verbosity(TrackingVerbosity.SUMMARISED).build());
        rec.logRequest("SET", "user:1", 0, "value");

        RequestResponseLog req = RequestResponseLogger.getAllLogs().get(0);
        assertThat(req.method().value()).isEqualTo("Set");
        assertThat(req.content()).isNull();
        assertThat(req.uri().toString()).isEqualTo("redis://db0/");
    }

    @Test
    void summarisedSkipsUnknownCommands() {
        RedisInteractionRecorder rec =
            new RedisInteractionRecorder(opts().verbosity(TrackingVerbosity.SUMMARISED).build());
        assertThat(rec.logRequest("WEIRDCMD", "k", 0, null)).isEmpty();
        assertThat(RequestResponseLogger.getAllLogs()).isEmpty();
    }

    @Test
    void noTestContextEmitsNothing() {
        RedisInteractionRecorder rec = new RedisInteractionRecorder(opts().testInfoFetcher(() -> null).build());
        assertThat(rec.logRequest("GET", "k", 0, null)).isEmpty();
    }
}
