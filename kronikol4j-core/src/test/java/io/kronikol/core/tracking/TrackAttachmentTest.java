package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.context.TestIdentityScope;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Verifies {@link Track#attachment(String, String)} resolves the test id and forwards to the registered
 *  {@link AttachmentSink} (the core→report seam), and is an inert no-op when nothing resolves. */
class TrackAttachmentTest {

    private final List<String[]> captured = new ArrayList<>();

    @BeforeEach
    void installSink() {
        captured.clear();
        Track.attachmentSink((testId, filePath, name) -> captured.add(new String[] {testId, filePath, name}));
    }

    @AfterEach
    void cleanup() {
        Track.attachmentSink(null);
        TestIdentityScope.clear();
    }

    @Test
    void forwardsTestIdFilePathAndNameToTheSink() {
        try (var scope = TestIdentityScope.begin("My Test", "t1")) {
            Track.attachment("/tmp/receipt.pdf", "Receipt");
        }

        assertThat(captured).singleElement().satisfies(a -> {
            assertThat(a[0]).isEqualTo("t1");
            assertThat(a[1]).isEqualTo("/tmp/receipt.pdf");
            assertThat(a[2]).isEqualTo("Receipt");
        });
    }

    @Test
    void nullNameIsPassedThroughForTheSinkToDerive() {
        try (var scope = TestIdentityScope.begin("My Test", "t1")) {
            Track.attachment("/tmp/receipt.pdf");
        }

        assertThat(captured).singleElement().satisfies(a -> assertThat(a[2]).isNull());
    }

    @Test
    void noOpWhenNoTestIdentityResolves() {
        TestIdentityScope.clear();
        Track.attachment("/tmp/x.pdf", "X"); // no ambient scope, no resolver

        assertThat(captured).isEmpty();
    }

    @Test
    void noOpWhenNoSinkInstalled() {
        Track.attachmentSink(null);
        try (var scope = TestIdentityScope.begin("My Test", "t1")) {
            Track.attachment("/tmp/x.pdf", "X");
        }
        // No sink override and no ServiceLoader provider on the core test classpath → silently ignored.
        assertThat(captured).isEmpty();
    }
}
