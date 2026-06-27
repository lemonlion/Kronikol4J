package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestPhaseContext;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Mirrors .NET {@code PhaseVariantExtensions} ({@code AttachVariants}/{@code WithVariants}).
 *
 * <p>Behaviour from the source of truth: variants are attached <em>only</em> when the ambient phase is
 * {@link TestPhase#UNKNOWN} (the renderer will pick later) <em>and</em> at least one of the
 * setup/action verbosity overrides is non-null. Each variant is built from its own override, falling
 * back to the base verbosity when that override is null.
 */
class PhaseVariantExtensionsTest {

    /** A builder that encodes the chosen verbosity into the variant content, so tests can assert it. */
    private static final Function<TrackingVerbosity, PhaseVariant> BUILDER =
        v -> new PhaseVariant(Method.Http.GET, URI.create("test://x"), "v=" + v, List.of(), false);

    @AfterEach
    void cleanup() {
        TestPhaseContext.reset();
    }

    private static RequestResponseLog newLog() {
        return RequestResponseLog.builder()
            .testName("t").testId("id")
            .method(Method.Http.GET).uri(URI.create("test://x"))
            .serviceName("svc").callerName("caller")
            .type(RequestResponseType.REQUEST)
            .traceId(new UUID(0, 1)).requestResponseId(new UUID(0, 2))
            .build();
    }

    @Test
    void doesNothingWhenPhaseIsKnown() {
        TestPhaseContext.set(TestPhase.ACTION);
        RequestResponseLog log = newLog();

        PhaseVariantExtensions.attachVariants(
            log, TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, TrackingVerbosity.SUMMARISED, BUILDER);

        assertThat(log.setupVariant()).isNull();
        assertThat(log.actionVariant()).isNull();
    }

    @Test
    void doesNothingWhenNoOverrideConfigured() {
        TestPhaseContext.set(TestPhase.UNKNOWN);
        RequestResponseLog log = newLog();

        PhaseVariantExtensions.attachVariants(log, TrackingVerbosity.DETAILED, null, null, BUILDER);

        assertThat(log.setupVariant()).isNull();
        assertThat(log.actionVariant()).isNull();
    }

    @Test
    void attachesBothVariantsWhenPhaseUnknownAndAnOverrideIsSet() {
        TestPhaseContext.set(TestPhase.UNKNOWN);
        RequestResponseLog log = newLog();

        // Only the setup override is configured; the action variant must fall back to the base verbosity.
        PhaseVariantExtensions.attachVariants(
            log, TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, null, BUILDER);

        assertThat(log.setupVariant()).isNotNull();
        assertThat(log.setupVariant().content()).isEqualTo("v=RAW");
        assertThat(log.actionVariant()).isNotNull();
        assertThat(log.actionVariant().content()).isEqualTo("v=DETAILED");
    }

    @Test
    void eachVariantUsesItsOwnOverride() {
        TestPhaseContext.set(TestPhase.UNKNOWN);
        RequestResponseLog log = newLog();

        PhaseVariantExtensions.attachVariants(
            log, TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, TrackingVerbosity.SUMMARISED, BUILDER);

        assertThat(log.setupVariant().content()).isEqualTo("v=RAW");
        assertThat(log.actionVariant().content()).isEqualTo("v=SUMMARISED");
    }

    @Test
    void withVariantsIsFluentAndAttaches() {
        TestPhaseContext.set(TestPhase.UNKNOWN);
        RequestResponseLog log = newLog();

        RequestResponseLog returned = PhaseVariantExtensions.withVariants(
            log, TrackingVerbosity.DETAILED, TrackingVerbosity.RAW, null, BUILDER);

        assertThat(returned).isSameAs(log);
        assertThat(returned.setupVariant().content()).isEqualTo("v=RAW");
        assertThat(returned.actionVariant().content()).isEqualTo("v=DETAILED");
    }
}
