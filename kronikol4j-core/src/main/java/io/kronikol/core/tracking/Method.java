package io.kronikol.core.tracking;

import java.util.Objects;

/**
 * The "method" of an interaction — either a standard HTTP verb or an arbitrary custom verb
 * (e.g. {@code "QUERY"}, {@code "INSERT"}, {@code "PUBLISH"}). This is the Java-idiomatic
 * encoding of the .NET {@code OneOf<HttpMethod, string>} (plan §3.1): a sealed interface with
 * pattern-matchable cases instead of a discriminated union.
 */
public sealed interface Method permits Method.Http, Method.Custom {

    /** The wire/display value of this method (always upper-case for HTTP verbs). */
    String value();

    /** A custom, non-HTTP verb. */
    static Method of(String customVerb) {
        return new Custom(customVerb);
    }

    /** Standard HTTP verbs; {@link #value()} returns the upper-case name (e.g. {@code "POST"}). */
    enum Http implements Method {
        GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS, TRACE, CONNECT, QUERY;

        @Override
        public String value() {
            return name();
        }
    }

    /** An arbitrary verb that is not a standard HTTP method. */
    record Custom(String value) implements Method {
        public Custom {
            Objects.requireNonNull(value, "value");
        }
    }
}
