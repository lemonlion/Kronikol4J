package io.kronikol.core.tracking;

import java.net.URI;
import java.util.List;

/**
 * A pre-computed, phase-specific rendering of an interaction (e.g. a terser Setup-phase form).
 * Lets the report layer pick the right variant without rebuilding the log. Mirrors the .NET
 * {@code PhaseVariant} record.
 *
 * @param skip when {@code true}, this interaction is not rendered for the corresponding phase.
 */
public record PhaseVariant(Method method, URI uri, String content, List<Header> headers, boolean skip) {

    public PhaseVariant {
        headers = headers == null ? List.of() : List.copyOf(headers);
    }
}
