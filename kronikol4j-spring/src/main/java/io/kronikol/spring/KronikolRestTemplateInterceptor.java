package io.kronikol.spring;

import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.http.HttpExchangeRecorder;
import io.kronikol.http.HttpTrackingOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/**
 * A Spring {@code RestTemplate} interceptor that records each outgoing HTTP exchange (plan §7),
 * delegating to {@link HttpExchangeRecorder}. The response is buffered so reading its body for the
 * diagram does not consume the stream the caller needs.
 *
 * <pre>{@code restTemplate.getInterceptors().add(new KronikolRestTemplateInterceptor(
 *         HttpTrackingOptions.forService("OrderService")));}</pre>
 */
public final class KronikolRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    private final HttpTrackingOptions options;

    public KronikolRestTemplateInterceptor(HttpTrackingOptions options) {
        this.options = options;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        BufferedResponse response = new BufferedResponse(execution.execute(request, body));

        String requestBody = (body == null || body.length == 0)
            ? null : new String(body, StandardCharsets.UTF_8);
        String responseBody = response.bodyBytes().length == 0
            ? null : new String(response.bodyBytes(), StandardCharsets.UTF_8);

        HttpExchangeRecorder.record(options, mapMethod(request.getMethod()), request.getURI(),
            toHeaders(request.getHeaders()), requestBody,
            StatusCode.of(response.getStatusCode().value()), responseBody);

        return response;
    }

    static Method mapMethod(HttpMethod method) {
        if (method == null) {
            return Method.of("UNKNOWN");
        }
        for (Method.Http verb : Method.Http.values()) {
            if (verb.name().equals(method.name())) {
                return verb;
            }
        }
        return Method.of(method.name());
    }

    private static List<Header> toHeaders(HttpHeaders headers) {
        List<Header> result = new ArrayList<>();
        headers.forEach((name, values) -> result.add(new Header(name, String.join(", ", values))));
        return result;
    }

    /** Buffers the response body so the diagram can read it without consuming the caller's stream. */
    static final class BufferedResponse implements ClientHttpResponse {
        private final ClientHttpResponse delegate;
        private final byte[] body;

        BufferedResponse(ClientHttpResponse delegate) throws IOException {
            this.delegate = delegate;
            InputStream stream = delegate.getBody();
            this.body = stream == null ? new byte[0] : stream.readAllBytes();
        }

        byte[] bodyBytes() {
            return body;
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
