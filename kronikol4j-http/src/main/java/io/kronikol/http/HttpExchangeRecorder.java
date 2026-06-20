package io.kronikol.http;

import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Records one HTTP request/response exchange as a tracked interaction pair. This is the reusable
 * core that wire-level interceptors (OkHttp {@code Interceptor}, Spring
 * {@code ClientHttpRequestInterceptor}, a {@code java.net.http} wrapper) delegate to — keeping the
 * actual HTTP-library dependency out of this module (it would be {@code compileOnly} in the
 * interceptor adapter). Implements the LogPair auto-resolve ingestion pattern (plan §1/§3.4).
 */
public final class HttpExchangeRecorder {

    private HttpExchangeRecorder() {
    }

    public static void record(HttpTrackingOptions options,
                              Method method,
                              URI uri,
                              List<Header> requestHeaders,
                              String requestBody,
                              StatusCode statusCode,
                              String responseBody) {
        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return; // outside a test context — the exchange still happens, just untracked
        }

        UUID trace = UUID.randomUUID();
        UUID rr = UUID.randomUUID();
        TestPhase phase = TestPhaseContext.current();

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri).headers(requestHeaders)
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(rr)
            .dependencyCategory(options.dependencyCategory()).content(requestBody)
            .phase(phase).build());

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri)
            .serviceName(options.serviceName()).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(rr)
            .statusCode(statusCode).dependencyCategory(options.dependencyCategory()).content(responseBody)
            .phase(phase).build());
    }
}
