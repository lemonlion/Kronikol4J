package io.kronikol.core.tracking;

import java.util.Objects;

/**
 * The status of a response — either a numeric HTTP status or an arbitrary custom string.
 * Java-idiomatic encoding of the .NET {@code OneOf<HttpStatusCode, string>} (plan §3.1).
 *
 * <p>Rendering the .NET-style label for an HTTP code (e.g. {@code 404 -> "Not Found"}) is a
 * parity concern handled in the diagram module's formatter, not here.
 */
public sealed interface StatusCode permits StatusCode.Http, StatusCode.Custom {

    static StatusCode of(int httpCode) {
        return new Http(httpCode);
    }

    static StatusCode of(String custom) {
        return new Custom(custom);
    }

    /** A numeric HTTP status code. */
    record Http(int code) implements StatusCode {
    }

    /** An arbitrary, non-HTTP status string. */
    record Custom(String value) implements StatusCode {
        public Custom {
            Objects.requireNonNull(value, "value");
        }
    }
}
