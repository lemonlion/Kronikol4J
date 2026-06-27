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
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * An OkHttp {@link Interceptor} that auto-captures each outgoing HTTP exchange as a tracked
 * request/response pair — the Java analog of the .NET {@code TestTrackingMessageHandler}. Install it as an
 * application interceptor on the {@code OkHttpClient}.
 *
 * <p>Per exchange it: resolves the participant name (via {@link ServiceNameResolver}), skips excluded hosts
 * and phase-suppressed calls, stamps the test-identity + trace headers and a W3C {@code traceparent} so a
 * downstream tracked service joins the trace, captures request/response bodies subject to the configured
 * {@link io.kronikol.core.tracking.TrackingVerbosity}, and emits the pair through
 * {@link RequestResponseLogger}. The OkHttp dependency is {@code compileOnly}.
 */
public final class KronikolOkHttpInterceptor implements Interceptor {

    /** Cap on response bytes peeked for note content — avoids buffering huge payloads into memory. */
    private static final long MAX_PEEK_BYTES = 1_000_000L;

    private final HttpTrackingConfig options;
    private final ServiceNameResolver resolver;

    public KronikolOkHttpInterceptor(HttpTrackingConfig options) {
        this.options = options;
        this.resolver = ServiceNameResolver.builder()
            .fixedName(options.fixedServiceName())
            .clientName(options.clientName())
            .clientNamesToServiceNames(options.clientNamesToServiceNames())
            .portsToServiceNames(options.portsToServiceNames())
            .build();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String host = request.url().host();

        // Skip tracking (but still forward the request) for excluded hosts or phase-suppressed calls.
        if (options.excludedHosts().excludes(host)
            || !PhaseConfiguration.shouldTrack(options.trackDuringSetup(), options.trackDuringAction())) {
            return chain.proceed(request);
        }

        TestInfo who = TestInfoResolver.resolve(options.testInfoFetcher());
        if (who == null) {
            return chain.proceed(request); // outside a test context — forward untracked
        }

        UUID trace = options.ids().newId();
        UUID requestResponseId = options.ids().newId();

        Request.Builder rb = request.newBuilder()
            .header(TrackingHeaders.CURRENT_TEST_NAME, who.name())
            .header(TrackingHeaders.CURRENT_TEST_ID, who.id())
            .header(TrackingHeaders.TRACE_ID, trace.toString());
        if (options.callerName() != null) {
            rb.header(TrackingHeaders.CALLER_NAME, options.callerName());
        }

        String activityTraceId = null;
        String activitySpanId = null;
        if (options.injectTraceparent() && request.header("traceparent") == null) {
            W3CTraceparent tp = W3CTraceparent.generate(options.ids());
            rb.header("traceparent", tp.header());
            activityTraceId = tp.traceId();
            activitySpanId = tp.spanId();
        }

        Request tracked = rb.build();

        // Consume any ambient DiagramFocus up-front (both halves), matching .NET — so focus set during the
        // call doesn't leak into the response note.
        List<String> requestFocus = io.kronikol.core.tracking.DiagramFocus.consumePendingRequestFocus();
        List<String> responseFocus = io.kronikol.core.tracking.DiagramFocus.consumePendingResponseFocus();

        boolean withBody = options.verbosity().includesPayload();
        String requestBody = withBody ? readRequestBody(tracked) : null;
        List<Header> requestHeaders = toHeaders(tracked);
        URI uri = tracked.url().uri();
        Method method = methodOf(tracked.method());
        String serviceName = resolver.resolve(tracked.url().port());
        TestPhase phase = TestPhaseContext.current();

        Response response = chain.proceed(tracked);

        String responseBody = withBody ? peekResponseBody(response) : null;
        StatusCode statusCode = StatusCode.of(response.code());

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri).headers(requestHeaders)
            .serviceName(serviceName).callerName(options.callerName())
            .type(RequestResponseType.REQUEST).traceId(trace).requestResponseId(requestResponseId)
            .dependencyCategory(options.dependencyCategory()).content(requestBody).phase(phase)
            .focusFields(requestFocus).build()
            .activityTraceId(activityTraceId).activitySpanId(activitySpanId));

        RequestResponseLogger.log(RequestResponseLog.builder()
            .testInfo(who).method(method).uri(uri)
            .serviceName(serviceName).callerName(options.callerName())
            .type(RequestResponseType.RESPONSE).traceId(trace).requestResponseId(requestResponseId)
            .statusCode(statusCode).dependencyCategory(options.dependencyCategory()).content(responseBody)
            .phase(phase).focusFields(responseFocus).build()
            .activityTraceId(activityTraceId).activitySpanId(activitySpanId));

        return response;
    }

    private static String readRequestBody(Request request) {
        RequestBody body = request.body();
        if (body == null || body.isOneShot() || isDuplex(body)) {
            return null;
        }
        try {
            Buffer buffer = new Buffer();
            body.writeTo(buffer);
            return buffer.readUtf8();
        } catch (Exception e) {
            return null; // unreadable body — capture nothing rather than fail the call
        }
    }

    private static boolean isDuplex(RequestBody body) {
        try {
            return body.isDuplex();
        } catch (Throwable ignored) {
            return false; // older OkHttp without isDuplex
        }
    }

    private static String peekResponseBody(Response response) {
        try {
            return response.peekBody(MAX_PEEK_BYTES).string();
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Header> toHeaders(Request request) {
        okhttp3.Headers headers = request.headers();
        List<Header> out = new ArrayList<>(headers.size());
        for (int i = 0; i < headers.size(); i++) {
            out.add(new Header(headers.name(i), headers.value(i)));
        }
        return out;
    }

    private static Method methodOf(String httpMethod) {
        try {
            return Method.Http.valueOf(httpMethod);
        } catch (IllegalArgumentException notStandard) {
            return Method.of(httpMethod);
        }
    }
}
