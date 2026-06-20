package io.kronikol.assertj;

import io.kronikol.core.tracking.Track;
import org.assertj.core.api.SoftAssertions;

/**
 * A drop-in {@link SoftAssertions} that records every collected assertion failure as a tracked note
 * — Tier 1 of assertion tracking (plan §3.9), with <strong>zero bytecode weaving</strong>. Use it
 * exactly like AssertJ's {@code SoftAssertions}:
 *
 * <pre>{@code
 * var softly = new KronikolSoftAssertions();
 * softly.assertThat(order.status()).isEqualTo(CONFIRMED);
 * softly.assertThat(order.total()).isEqualTo(42);
 * softly.assertAll();
 * }</pre>
 *
 * Each failure is captured via AssertJ's {@code AfterAssertionErrorCollected} SPI as it is collected.
 */
public class KronikolSoftAssertions extends SoftAssertions {

    public KronikolSoftAssertions() {
        setAfterAssertionErrorCollected(error ->
            Track.record(firstLine(error.getMessage()), false, error.getMessage()));
    }

    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "assertion failed";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message.strip() : message.substring(0, newline).strip();
    }
}
