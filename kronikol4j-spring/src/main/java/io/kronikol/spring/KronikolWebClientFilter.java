package io.kronikol.spring;

import io.kronikol.core.constants.TrackingHeaders;
import io.kronikol.core.context.PhaseConfiguration;
import io.kronikol.core.context.TestInfo;
import io.kronikol.core.context.TestInfoResolver;
import io.kronikol.core.context.TestPhaseContext;
import io.kronikol.core.naming.ServiceNameResolver;
import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseLogger;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.core.tracking.TestPhase;
import io.kronikol.core.tracking.W3CTraceparent;
import io.kronikol.http.HttpTrackingConfig;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * A Spring {@link ExchangeFilterFunction} that auto-captures each {@code WebClient} exchange as a tracked
 * request/response pair — the reactive analog of {@link KronikolRestTemplateInterceptor}. Register it via
 * {@code WebClient.builder().filter(new KronikolWebClientFilter(config)).build()}.
 *
 * <p>Per exchange it skips excluded hosts / phase-suppressed calls, resolves the participant name, stamps the
 * test-identity + trace headers and a W3C {@code traceparent}, and records the pair through
 * {@link RequestResponseLogger}. The response body is captured by buffering it and re-supplying it to the
 * caller (so the downstream subscriber still reads it). spring-webflux is {@code compileOnly}.
 *
 * <p><strong>Request-body capture.</strong> A {@code WebClient} request body is a write-only reactive
 * {@code BodyInserter}; reading it back is only possible at the {@code ClientHttpConnector} layer, so request
 * bodies are not captured here (response bodies and all metadata are). The OkHttp / JDK adapters capture both.
 */
public final class KronikolWebClientFilter implements ExchangeFilterFunction {

    private final HttpTrackingConfig config;
    private final ServiceNameResolver resolver;

    public KronikolWebClientFilter(HttpTrackingConfig config) {
        this.config = config;
        this.resolver = ServiceNameResolver.builder()
            .fixedName(config.fixedServiceName())
            .clientName(config.clientName())
            .clientNamesToServiceNames(config.clientNamesToServiceNames())
            .portsToServiceNames(config.portsToServiceNames())
            .build();
    }

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        URI uri = request.url();
        if (config.excludedHosts().excludes(uri.getHost())
            || !PhaseConfiguration.shouldTrack(config.trackDuringSetup(), config.trackDuringAction())) {
            return next.exchange(request);
        }
        TestInfo who = TestInfoResolver.resolve(config.testInfoFetcher());
        if (who == null) {
            return next.exchange(request);
        }

        UUID traceId = config.ids().newId();
        UUID requestResponseId = config.ids().newId();

        ClientRequest.Builder rb = ClientRequest.from(request)
            .header(TrackingHeaders.CURRENT_TEST_NAME, who.name())
            .header(TrackingHeaders.CURRENT_TEST_ID, who.id())
            .header(TrackingHeaders.TRACE_ID, traceId.toString());
        if (config.callerName() != null) {
            rb.header(TrackingHeaders.CALLER_NAME, config.callerName());
        }

        String activityTraceId = null;
        String activitySpanId = null;
        if (config.injectTraceparent() && request.headers().getFirst("traceparent") == null) {
            W3CTraceparent tp = W3CTraceparent.generate(config.ids());
            rb.header("traceparent", tp.header());
            activityTraceId = tp.traceId();
            activitySpanId = tp.spanId();
        }

        ClientRequest tracked = rb.build();
        Method method = methodOf(request.method().name());
        String serviceName = resolver.resolve(effectivePort(uri));
        List<Header> requestHeaders = toHeaders(tracked);
        TestPhase phase = TestPhaseContext.current();
        boolean withBody = config.effectiveVerbosity().includesPayload();

        // Effectively-final copies for the lambda.
        String traceparentTrace = activityTraceId;
        String traceparentSpan = activitySpanId;

        return next.exchange(tracked).flatMap(response -> {
            StatusCode statusCode = StatusCode.of(response.statusCode().value());
            if (!withBody) {
                emit(who, method, uri, requestHeaders, serviceName, traceId, requestResponseId,
                    null, null, statusCode, phase, traceparentTrace, traceparentSpan);
                return Mono.just(response);
            }
            return response.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
                String responseBody = body.isEmpty() ? null : body;
                emit(who, method, uri, requestHeaders, serviceName, traceId, requestResponseId,
                    null, responseBody, statusCode, phase, traceparentTrace, traceparentSpan);
                // Re-supply the consumed body so the caller's subscriber can still read it.
                return ClientResponse.from(response).body(body).build();
            });
        });
    }

    private void emit(TestInfo who, Method method, URI uri, List<Header> requestHeaders, String serviceName,
                      UUID traceId, UUID requestResponseId, String requestBody, String responseBody,
                      StatusCode statusCode, TestPhase phase, String activityTraceId, String activitySpanId) {
        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri).headers(requestHeaders)
            .serviceName(serviceName).callerName(config.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .dependencyCategory(config.dependencyCategory()).content(requestBody).phase(phase).build()
            .activityTraceId(activityTraceId).activitySpanId(activitySpanId));

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri)
            .serviceName(serviceName).callerName(config.callerName())
            .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
            .statusCode(statusCode).dependencyCategory(config.dependencyCategory()).content(responseBody)
            .phase(phase).build()
            .activityTraceId(activityTraceId).activitySpanId(activitySpanId));
    }

    private static List<Header> toHeaders(ClientRequest request) {
        List<Header> out = new ArrayList<>();
        request.headers().forEach((name, values) -> values.forEach(v -> out.add(new Header(name, v))));
        return out;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static Method methodOf(String httpMethod) {
        try {
            return Method.Http.valueOf(httpMethod);
        } catch (IllegalArgumentException notStandard) {
            return Method.of(httpMethod);
        }
    }
}
