package io.kronikol.core.tracking;

import java.util.Objects;

/**
 * A single request/response header. The value may be {@code null} (mirrors the .NET
 * {@code (string Key, string? Value)} tuple).
 */
public record Header(String key, String value) {

    public Header {
        Objects.requireNonNull(key, "key");
    }
}
