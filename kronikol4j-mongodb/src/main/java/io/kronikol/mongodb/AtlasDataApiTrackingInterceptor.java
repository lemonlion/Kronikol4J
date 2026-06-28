package io.kronikol.mongodb;

import io.kronikol.core.tracking.Header;
import io.kronikol.mongodb.AtlasDataApiTracking.AtlasDataApiTrackingOptions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

/**
 * An OkHttp {@link Interceptor} that auto-captures MongoDB Atlas Data API (REST) calls — the Java analog of
 * the .NET {@code AtlasDataApiTrackingMessageHandler} ({@code DelegatingHandler}). Install it on the
 * {@code OkHttpClient} used for the Data API:
 *
 * <pre>{@code new OkHttpClient.Builder()
 *         .addInterceptor(new AtlasDataApiTrackingInterceptor(
 *             AtlasDataApiTrackingOptions.forService("Atlas").withTestInfoFetcher(...)))
 *         .build();}</pre>
 *
 * <p>It buffers the JSON request body (so it can be classified and logged), captures the response body, and
 * delegates to {@link AtlasDataApiTracking#record} — which classifies the {@code /action/{name}} request,
 * applies the excluded-operation / phase / Summarised-Other / identity gates, and emits the pair. The OkHttp
 * dependency is {@code compileOnly}.
 */
public final class AtlasDataApiTrackingInterceptor implements Interceptor {

    /** Cap on response bytes peeked for note content — avoids buffering huge payloads into memory. */
    private static final long MAX_PEEK_BYTES = 1_000_000L;

    private final AtlasDataApiTrackingOptions options;

    public AtlasDataApiTrackingInterceptor(AtlasDataApiTrackingOptions options) {
        this.options = options;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        String requestBody = readRequestBody(request);
        List<Header> requestHeaders = toHeaders(request);

        Response response = chain.proceed(request);

        String responseBody = peekResponseBody(response);
        AtlasDataApiTracking.record(options, request.method(), request.url().uri(),
            requestBody, requestHeaders, responseBody, response.code());

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
}
