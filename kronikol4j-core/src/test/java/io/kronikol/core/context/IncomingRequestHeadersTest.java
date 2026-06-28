package io.kronikol.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the {@link IncomingRequestHeaders} ambient scope: lookup, nesting/restore, mandatory clearing,
 *  and that a throwing lookup is swallowed. */
class IncomingRequestHeadersTest {

    @AfterEach
    void clear() {
        IncomingRequestHeaders.clear();
    }

    @Test
    void nullWhenNoScopeActive() {
        assertThat(IncomingRequestHeaders.get("X-Trace")).isNull();
    }

    @Test
    void exposesTheCurrentRequestHeaders() {
        Map<String, String> incoming = Map.of("X-Tenant", "acme", "X-Region", "eu");
        try (var scope = IncomingRequestHeaders.begin(incoming::get)) {
            assertThat(IncomingRequestHeaders.get("X-Tenant")).isEqualTo("acme");
            assertThat(IncomingRequestHeaders.get("X-Region")).isEqualTo("eu");
            assertThat(IncomingRequestHeaders.get("X-Absent")).isNull();
        }
        assertThat(IncomingRequestHeaders.get("X-Tenant")).isNull(); // cleared on close
    }

    @Test
    void scopesNestAndRestoreThePrevious() {
        try (var outer = IncomingRequestHeaders.begin(n -> "outer")) {
            try (var inner = IncomingRequestHeaders.begin(n -> "inner")) {
                assertThat(IncomingRequestHeaders.get("h")).isEqualTo("inner");
            }
            assertThat(IncomingRequestHeaders.get("h")).isEqualTo("outer"); // previous restored
        }
        assertThat(IncomingRequestHeaders.get("h")).isNull();
    }

    @Test
    void throwingLookupIsSwallowed() {
        try (var scope = IncomingRequestHeaders.begin(n -> {
            throw new IllegalStateException("no request bound");
        })) {
            assertThat(IncomingRequestHeaders.get("h")).isNull(); // never propagates to the caller
        }
    }
}
