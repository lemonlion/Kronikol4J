package io.kronikol.http;

import io.kronikol.core.context.IncomingRequestHeaders;
import io.kronikol.core.tracking.Header;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the {@code headersToForward} values for an outgoing request: for each configured header name, the
 * value of that header on the <em>incoming</em> server request (via {@link IncomingRequestHeaders}), when
 * present. The Java analog of the .NET {@code TestTrackingMessageHandler.ForwardHeaders} — every HTTP client
 * adapter calls this and adds the returned headers to the request it is about to send.
 */
public final class ForwardedHeaders {

    private ForwardedHeaders() {
    }

    /** The {@code (name, value)} pairs to add, in {@code names} order; empty when none configured/present. */
    public static List<Header> collect(List<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }
        List<Header> out = new ArrayList<>();
        for (String name : names) {
            String value = IncomingRequestHeaders.get(name);
            if (value != null) {
                out.add(new Header(name, value));
            }
        }
        return out;
    }
}
