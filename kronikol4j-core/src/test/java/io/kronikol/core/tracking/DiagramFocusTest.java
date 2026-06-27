package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies the {@link DiagramFocus} ambient consume-once mechanism (the .NET {@code DiagramFocus} analog). */
class DiagramFocusTest {

    @AfterEach
    void cleanup() {
        DiagramFocus.clearAll();
    }

    @Test
    void requestFocusIsConsumedOnce() {
        DiagramFocus.request("orderId", "total");
        assertThat(DiagramFocus.consumePendingRequestFocus()).containsExactly("orderId", "total");
        assertThat(DiagramFocus.consumePendingRequestFocus()).isNull(); // consumed
    }

    @Test
    void responseFocusIsIndependentOfRequestFocus() {
        DiagramFocus.request("a");
        DiagramFocus.response("b", "c");
        assertThat(DiagramFocus.consumePendingResponseFocus()).containsExactly("b", "c");
        assertThat(DiagramFocus.consumePendingRequestFocus()).containsExactly("a"); // request still pending
    }

    @Test
    void consumeWithoutSetReturnsNull() {
        assertThat(DiagramFocus.consumePendingRequestFocus()).isNull();
        assertThat(DiagramFocus.consumePendingResponseFocus()).isNull();
    }

    @Test
    void clearAllDiscardsPendingFocus() {
        DiagramFocus.request("x");
        DiagramFocus.response("y");
        DiagramFocus.clearAll();
        assertThat(DiagramFocus.consumePendingRequestFocus()).isNull();
        assertThat(DiagramFocus.consumePendingResponseFocus()).isNull();
    }

    @Test
    void noArgsSetsEmptyFocus() {
        DiagramFocus.request();
        assertThat(DiagramFocus.consumePendingRequestFocus()).isEmpty();
    }

    @Test
    void consumedFocusFeedsTheLogFocusFields() {
        DiagramFocus.request("orderId");
        List<String> focus = DiagramFocus.consumePendingRequestFocus();
        RequestResponseLog log = RequestResponseLog.builder()
            .testName("T").testId("1").method(Method.of("POST"))
            .uri(java.net.URI.create("http://svc/x")).serviceName("Svc").callerName("Test")
            .type(RequestResponseType.REQUEST)
            .traceId(java.util.UUID.randomUUID()).requestResponseId(java.util.UUID.randomUUID())
            .focusFields(focus).build();
        assertThat(log.focusFields()).containsExactly("orderId");
    }
}
