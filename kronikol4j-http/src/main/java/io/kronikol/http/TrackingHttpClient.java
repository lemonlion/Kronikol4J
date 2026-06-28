package io.kronikol.http;

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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A {@link HttpClient} decorator that auto-captures each exchange made through it as a tracked
 * request/response pair — the {@code java.net.http} analog of {@link KronikolOkHttpInterceptor}. Wrap your
 * real client: {@code HttpClient tracked = new TrackingHttpClient(HttpClient.newHttpClient(), config);}.
 *
 * <p>{@code java.net.http} has no interceptor pipeline, so this decorator rebuilds each request with the
 * test-identity + trace headers and a W3C {@code traceparent}, resolves the participant name, tees the
 * request body through a capturing {@link HttpRequest.BodyPublisher}, sends via the delegate, then records
 * the pair (request body from the tee; response body when it is a {@code String}/{@code byte[]}).
 */
public final class TrackingHttpClient extends HttpClient {

    private final HttpClient delegate;
    private final HttpTrackingConfig config;
    private final ServiceNameResolver resolver;

    public TrackingHttpClient(HttpClient delegate, HttpTrackingConfig config) {
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
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
        Capture capture = prepare(request);
        if (capture == null) {
            return delegate.send(request, handler);
        }
        HttpResponse<T> response = delegate.send(capture.request, handler);
        record(capture, response);
        return response;
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
        Capture capture = prepare(request);
        if (capture == null) {
            return delegate.sendAsync(request, handler);
        }
        return delegate.sendAsync(capture.request, handler)
            .whenComplete((response, error) -> {
                if (response != null) {
                    record(capture, response);
                }
            });
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                            HttpResponse.BodyHandler<T> handler,
                                                            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        Capture capture = prepare(request);
        if (capture == null) {
            return delegate.sendAsync(request, handler, pushPromiseHandler);
        }
        return delegate.sendAsync(capture.request, handler, pushPromiseHandler)
            .whenComplete((response, error) -> {
                if (response != null) {
                    record(capture, response);
                }
            });
    }

    /** Holds everything captured at request time, ready to record once the response arrives. */
    private static final class Capture {
        final HttpRequest request;
        final CapturingBodyPublisher capturedBody; // null if no/omitted body
        final TestInfo who;
        final String serviceName;
        final Method method;
        final URI uri;
        final List<Header> requestHeaders;
        final UUID traceId;
        final UUID requestResponseId;
        final String activityTraceId;
        final String activitySpanId;
        final TestPhase phase;
        final boolean withBody;

        Capture(HttpRequest request, CapturingBodyPublisher capturedBody, TestInfo who, String serviceName,
                Method method, URI uri, List<Header> requestHeaders, UUID traceId, UUID requestResponseId,
                String activityTraceId, String activitySpanId, TestPhase phase, boolean withBody) {
            this.request = request;
            this.capturedBody = capturedBody;
            this.who = who;
            this.serviceName = serviceName;
            this.method = method;
            this.uri = uri;
            this.requestHeaders = requestHeaders;
            this.traceId = traceId;
            this.requestResponseId = requestResponseId;
            this.activityTraceId = activityTraceId;
            this.activitySpanId = activitySpanId;
            this.phase = phase;
            this.withBody = withBody;
        }
    }

    private Capture prepare(HttpRequest request) {
        URI uri = request.uri();
        String host = uri.getHost();
        if (config.excludedHosts().excludes(host)
            || !PhaseConfiguration.shouldTrack(config.trackDuringSetup(), config.trackDuringAction())) {
            return null;
        }
        TestInfo who = TestInfoResolver.resolve(config.testInfoFetcher());
        if (who == null) {
            return null;
        }

        UUID traceId = config.ids().newId();
        UUID requestResponseId = config.ids().newId();
        boolean withBody = config.effectiveVerbosity().includesPayload();

        HttpRequest.Builder rb = HttpRequest.newBuilder(uri);
        copyHeaders(request, rb);
        rb.setHeader(TrackingHeaders.CURRENT_TEST_NAME, who.name());
        rb.setHeader(TrackingHeaders.CURRENT_TEST_ID, who.id());
        rb.setHeader(TrackingHeaders.TRACE_ID, traceId.toString());
        if (config.callerName() != null) {
            rb.setHeader(TrackingHeaders.CALLER_NAME, config.callerName());
        }

        String activityTraceId = null;
        String activitySpanId = null;
        boolean hasTraceparent = request.headers().firstValue("traceparent").isPresent();
        if (config.injectTraceparent() && !hasTraceparent) {
            W3CTraceparent tp = W3CTraceparent.generate(config.ids());
            rb.setHeader("traceparent", tp.header());
            activityTraceId = tp.traceId();
            activitySpanId = tp.spanId();
        }

        request.timeout().ifPresent(rb::timeout);
        request.version().ifPresent(rb::version);
        rb.expectContinue(request.expectContinue());

        Optional<HttpRequest.BodyPublisher> publisher = request.bodyPublisher();
        CapturingBodyPublisher capturing = null;
        if (publisher.isPresent()) {
            if (withBody) {
                capturing = new CapturingBodyPublisher(publisher.get());
                rb.method(request.method(), capturing);
            } else {
                rb.method(request.method(), publisher.get());
            }
        } else {
            rb.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }

        HttpRequest tracked = rb.build();
        return new Capture(tracked, capturing, who, resolver.resolve(effectivePort(uri)),
            methodOf(request.method()), uri, toHeaders(tracked), traceId, requestResponseId,
            activityTraceId, activitySpanId, TestPhaseContext.current(), withBody);
    }

    private void record(Capture c, HttpResponse<?> response) {
        String requestBody = c.withBody && c.capturedBody != null ? c.capturedBody.captured() : null;
        String responseBody = c.withBody ? stringify(response.body()) : null;
        StatusCode statusCode = StatusCode.of(response.statusCode());

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(c.who).method(c.method).uri(c.uri).headers(c.requestHeaders)
            .serviceName(c.serviceName).callerName(config.callerName())
            .type(RequestResponseType.REQUEST).traceId(c.traceId).requestResponseId(c.requestResponseId)
            .dependencyCategory(config.dependencyCategory()).content(requestBody).phase(c.phase).build()
            .activityTraceId(c.activityTraceId).activitySpanId(c.activitySpanId));

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(c.who).method(c.method).uri(c.uri)
            .serviceName(c.serviceName).callerName(config.callerName())
            .type(RequestResponseType.RESPONSE).traceId(c.traceId).requestResponseId(c.requestResponseId)
            .statusCode(statusCode).dependencyCategory(config.dependencyCategory()).content(responseBody)
            .phase(c.phase).build()
            .activityTraceId(c.activityTraceId).activitySpanId(c.activitySpanId));
    }

    private static void copyHeaders(HttpRequest request, HttpRequest.Builder rb) {
        for (Map.Entry<String, List<String>> e : request.headers().map().entrySet()) {
            for (String value : e.getValue()) {
                try {
                    rb.header(e.getKey(), value);
                } catch (IllegalArgumentException restricted) {
                    // java.net.http forbids setting certain headers (Host, Content-Length, …) — skip them.
                }
            }
        }
    }

    private static List<Header> toHeaders(HttpRequest request) {
        List<Header> out = new ArrayList<>();
        request.headers().map().forEach((k, vs) -> vs.forEach(v -> out.add(new Header(k, v))));
        return out;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String stringify(Object body) {
        if (body instanceof String s) {
            return s;
        }
        if (body instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null; // non-string response bodies (streams/files) are not captured as note content
    }

    private static Method methodOf(String httpMethod) {
        try {
            return Method.Http.valueOf(httpMethod);
        } catch (IllegalArgumentException notStandard) {
            return Method.of(httpMethod);
        }
    }

    /** A {@link HttpRequest.BodyPublisher} that tees published bytes into a buffer as the client sends them. */
    private static final class CapturingBodyPublisher implements HttpRequest.BodyPublisher {
        private final HttpRequest.BodyPublisher delegate;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();

        CapturingBodyPublisher(HttpRequest.BodyPublisher delegate) {
            this.delegate = delegate;
        }

        String captured() {
            return captured.toString(StandardCharsets.UTF_8);
        }

        @Override
        public long contentLength() {
            return delegate.contentLength();
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            delegate.subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    subscriber.onSubscribe(subscription);
                }

                @Override
                public void onNext(ByteBuffer item) {
                    ByteBuffer copy = item.duplicate();
                    byte[] bytes = new byte[copy.remaining()];
                    copy.get(bytes);
                    captured.writeBytes(bytes);
                    subscriber.onNext(item); // forward the original buffer untouched
                }

                @Override
                public void onError(Throwable throwable) {
                    subscriber.onError(throwable);
                }

                @Override
                public void onComplete() {
                    subscriber.onComplete();
                }
            });
        }
    }

    // --- pure delegation of the remaining HttpClient surface ---------------------------------------

    @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
    @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
    @Override public Redirect followRedirects() { return delegate.followRedirects(); }
    @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
    @Override public SSLContext sslContext() { return delegate.sslContext(); }
    @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
    @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
    @Override public Version version() { return delegate.version(); }
    @Override public Optional<Executor> executor() { return delegate.executor(); }
    @Override public WebSocket.Builder newWebSocketBuilder() { return delegate.newWebSocketBuilder(); }
}
