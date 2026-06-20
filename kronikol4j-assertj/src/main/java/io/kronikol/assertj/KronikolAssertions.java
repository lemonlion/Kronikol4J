package io.kronikol.assertj;

import io.kronikol.core.tracking.Track;
import org.assertj.core.api.Assertions;

/**
 * Global AssertJ description tracking (Tier 1, plan §3.9) — zero weaving. When enabled, every
 * <em>described</em> assertion ({@code assertThat(x).as("…")}) is recorded as a note via AssertJ's
 * {@code setDescriptionConsumer}, which fires for successful assertions and the first failure.
 *
 * <p>Enable once per run (e.g. in a {@code @BeforeAll} / test-run fixture). Because the description
 * consumer cannot reliably distinguish pass from fail, the precise failure capture comes from
 * {@link KronikolSoftAssertions}; this hook surfaces the <em>descriptions</em>.
 */
public final class KronikolAssertions {

    private KronikolAssertions() {
    }

    public static void enableDescriptionTracking() {
        Assertions.setDescriptionConsumer(description -> {
            String text = description.value();
            if (text != null && !text.isBlank()) {
                Track.record(text, true, null);
            }
        });
    }

    public static void disableDescriptionTracking() {
        Assertions.setDescriptionConsumer(null);
    }
}
