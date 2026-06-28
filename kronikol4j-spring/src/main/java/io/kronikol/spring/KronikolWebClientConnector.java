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
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ClientHttpRequest;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.http.client.reactive.ClientHttpResponse;
import org.springframework.http.client.reactive.ClientHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A Spring {@link ClientHttpConnector} decorator that auto-captures each {@code WebClient} exchange — the
 * transport-layer counterpart to {@link KronikolWebClientFilter}. Unlike the filter (which can only read the
 * response, since a {@code WebClient} request body is a write-only reactive {@code BodyInserter}), the
 * connector sees the request as it is written, so it captures <strong>both</strong> bodies — matching the
 * OkHttp / JDK adapters and the .NET {@code DelegatingHandler}. Register it via
 * {@code WebClient.builder().clientConnector(new KronikolWebClientConnector(delegate, config)).build()}.
 *
 * <p>Per exchange it skips excluded hosts / phase-suppressed calls, resolves the participant name, stamps the
 * test-identity + trace headers and a W3C {@code traceparent}, tees the request and response bodies without
 * consuming them (so the caller still reads the response), and records the pair through
 * {@link RequestResponseLogger}. spring-webflux + reactor are {@code compileOnly}.
 */
public final class KronikolWebClientConnector implements ClientHttpConnector {

    private final ClientHttpConnector delegate;
    private final HttpTrackingConfig config;
    private final ServiceNameResolver resolver;

    public KronikolWebClientConnector(ClientHttpConnector delegate, HttpTrackingConfig config) {
        this.delegate = delegate;
        this.config = config;
        this.resolver = ServiceNameResolver.builder()
            .fixedName(config.fixedServiceName())
            .clientName(config.clientName())
            .clientNamesToServiceNames(config.clientNamesToServiceNames())
            .portsToServiceNames(config.portsToServiceNames())
            .build();
    }

    @Override
    public Mono<ClientHttpResponse> connect(HttpMethod method, URI uri,
            Function<? super ClientHttpRequest, Mono<Void>> requestCallback) {
        if (config.excludedHosts().excludes(uri.getHost())
            || !PhaseConfiguration.shouldTrack(config.trackDuringSetup(), config.trackDuringAction())) {
            return delegate.connect(method, uri, requestCallback);
        }
        TestInfo who = TestInfoResolver.resolve(config.testInfoFetcher());
        if (who == null) {
            return delegate.connect(method, uri, requestCallback);
        }

        UUID traceId = config.ids().newId();
        UUID requestResponseId = config.ids().newId();
        boolean withBody = config.effectiveVerbosity().includesPayload();
        TestPhase phase = TestPhaseContext.current();
        Method httpMethod = methodOf(method.name());
        String serviceName = resolver.resolve(effectivePort(uri));
        Capture capture = new Capture();

        Function<ClientHttpRequest, Mono<Void>> wrapped = request -> {
            // Mutate headers via beforeCommit — the supported hook for a connector decorator (headers set
            // eagerly here would be discarded when the underlying request is committed).
            request.beforeCommit(() -> {
                HttpHeaders headers = request.getHeaders();
                headers.set(TrackingHeaders.CURRENT_TEST_NAME, who.name());
                headers.set(TrackingHeaders.CURRENT_TEST_ID, who.id());
                headers.set(TrackingHeaders.TRACE_ID, traceId.toString());
                if (config.callerName() != null) {
                    headers.set(TrackingHeaders.CALLER_NAME, config.callerName());
                }
                for (Header forwarded : io.kronikol.http.ForwardedHeaders.collect(config.headersToForward())) {
                    headers.set(forwarded.key(), forwarded.value());
                }
                if (config.injectTraceparent() && headers.getFirst("traceparent") == null) {
                    W3CTraceparent tp = W3CTraceparent.generate(config.ids());
                    headers.set("traceparent", tp.header());
                    capture.activityTraceId = tp.traceId();
                    capture.activitySpanId = tp.spanId();
                }
                capture.requestHeaders = snapshot(headers);
                return Mono.empty();
            });
            ClientHttpRequest tracked = withBody ? new CapturingRequest(request, capture.requestBody) : request;
            return requestCallback.apply(tracked);
        };

        return delegate.connect(method, uri, wrapped).map(response -> {
            capture.status = StatusCode.of(response.getStatusCode().value());
            if (!withBody) {
                emit(who, httpMethod, uri, serviceName, traceId, requestResponseId, capture, phase);
                return response;
            }
            return new CapturingResponse(response, capture.responseBody,
                () -> emit(who, httpMethod, uri, serviceName, traceId, requestResponseId, capture, phase));
        });
    }

    private void emit(TestInfo who, Method method, URI uri, String serviceName, UUID traceId,
                      UUID requestResponseId, Capture capture, TestPhase phase) {
        String requestBody = capture.requestBody.size() == 0
            ? null : capture.requestBody.toString(StandardCharsets.UTF_8);
        String responseBody = capture.responseBody.size() == 0
            ? null : capture.responseBody.toString(StandardCharsets.UTF_8);

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri).headers(capture.requestHeaders)
            .serviceName(serviceName).callerName(config.callerName())
            .type(RequestResponseType.REQUEST).traceId(traceId).requestResponseId(requestResponseId)
            .dependencyCategory(config.dependencyCategory()).content(requestBody).phase(phase).build()
            .activityTraceId(capture.activityTraceId).activitySpanId(capture.activitySpanId));

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri)
            .serviceName(serviceName).callerName(config.callerName())
            .type(RequestResponseType.RESPONSE).traceId(traceId).requestResponseId(requestResponseId)
            .statusCode(capture.status).dependencyCategory(config.dependencyCategory()).content(responseBody)
            .phase(phase).build()
            .activityTraceId(capture.activityTraceId).activitySpanId(capture.activitySpanId));
    }

    /** Copies a buffer's readable bytes into {@code out} without advancing its read position (peek-tee). */
    private static void teeInto(DataBuffer buffer, ByteArrayOutputStream out) {
        int position = buffer.readPosition();
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        buffer.readPosition(position);
        out.writeBytes(bytes);
    }

    private static List<Header> snapshot(HttpHeaders headers) {
        List<Header> out = new ArrayList<>();
        headers.forEach((name, values) -> values.forEach(v -> out.add(new Header(name, v))));
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

    /** Mutable per-exchange accumulator (request/response bodies + headers + status + trace ids). */
    private static final class Capture {
        final ByteArrayOutputStream requestBody = new ByteArrayOutputStream();
        final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        List<Header> requestHeaders = List.of();
        StatusCode status;
        String activityTraceId;
        String activitySpanId;
    }

    /** Tees the outgoing request body into {@code sink} as it is written, without consuming it. */
    private static final class CapturingRequest extends ClientHttpRequestDecorator {
        private final ByteArrayOutputStream sink;

        CapturingRequest(ClientHttpRequest delegate, ByteArrayOutputStream sink) {
            super(delegate);
            this.sink = sink;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).doOnNext(buffer -> teeInto(buffer, sink)));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return super.writeAndFlushWith(Flux.from(body)
                .map(inner -> Flux.from(inner).doOnNext(buffer -> teeInto(buffer, sink))));
        }
    }

    /** Tees the response body into {@code sink} (peek, not consume) and emits the pair when it completes. */
    private static final class CapturingResponse extends ClientHttpResponseDecorator {
        private final ByteArrayOutputStream sink;
        private final Runnable onComplete;
        private final AtomicBoolean emitted = new AtomicBoolean();

        CapturingResponse(ClientHttpResponse delegate, ByteArrayOutputStream sink, Runnable onComplete) {
            super(delegate);
            this.sink = sink;
            this.onComplete = onComplete;
        }

        @Override
        public Flux<DataBuffer> getBody() {
            return super.getBody()
                .doOnNext(buffer -> teeInto(buffer, sink))
                .doFinally(signal -> {
                    if (emitted.compareAndSet(false, true)) {
                        onComplete.run();
                    }
                });
        }
    }
}
