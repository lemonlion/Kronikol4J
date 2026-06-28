package io.kronikol.report.step;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import io.kronikol.core.tracking.Track;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.ScenarioStep;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the end-to-end {@code Track.attachment(...)} → {@code StepCollector} path is wired via the
 * {@link io.kronikol.core.tracking.AttachmentSink} {@code ServiceLoader} provider
 * ({@link StepCollectorAttachmentSink}) — no explicit sink installed, discovery does the work.
 */
class TrackAttachmentWiringTest {

    private static final String TID = "wiring-t1";

    @BeforeEach
    @AfterEach
    void reset() {
        StepCollector.clearSteps(TID);
        Track.attachmentSink(null); // rely on ServiceLoader discovery, not an explicit override
        TestIdentityScope.clear();
    }

    @Test
    void attachmentInsideAnActiveStepLandsOnThatStep() {
        try (var scope = TestIdentityScope.begin("Wiring Test", TID)) {
            StepCollector.startStep(TID, "When", "the receipt is generated", null, null);
            Track.attachment("/tmp/receipt.pdf", "Receipt");
            StepCollector.completeStep(TID, true, null);
        }

        List<ScenarioStep> steps = StepCollector.getSteps(TID);
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).attachments())
            .extracting(FileAttachment::name)
            .containsExactly("Receipt");
    }

    @Test
    void attachmentWithNoActiveStepLandsOnTheScenario() {
        try (var scope = TestIdentityScope.begin("Wiring Test", TID)) {
            Track.attachment("/tmp/log.txt"); // no name → derived from the path
        }

        assertThat(StepCollector.getScenarioAttachments(TID))
            .extracting(FileAttachment::name)
            .containsExactly("log.txt");
    }
}
