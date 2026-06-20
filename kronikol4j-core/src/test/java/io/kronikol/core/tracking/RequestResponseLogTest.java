package io.kronikol.core.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RequestResponseLogTest {

    private static RequestResponseLog.Builder minimal() {
        return RequestResponseLog.builder()
            .testName("MyTest").testId("id-1")
            .method(Method.Http.POST)
            .uri(URI.create("http://orders/checkout"))
            .serviceName("OrderService").callerName("Test")
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .requestResponseId(UUID.fromString("00000000-0000-0000-0000-000000000002"));
    }

    @Test
    void buildsWithCoreFields() {
        var log = minimal().content("{}").dependencyCategory("HTTP").build();
        assertThat(log.testName()).isEqualTo("MyTest");
        assertThat(log.method().value()).isEqualTo("POST");
        assertThat(log.metaType()).isEqualTo(RequestResponseMetaType.DEFAULT);
        assertThat(log.headers()).isEmpty();
        assertThat(log.phase()).isEqualTo(TestPhase.UNKNOWN);
    }

    @Test
    void missingRequiredFieldThrows() {
        assertThatThrownBy(() -> RequestResponseLog.builder().build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void enrichmentIsMutableAfterConstruction() {
        var log = minimal().build();
        log.phase(TestPhase.ACTION).noteOnRight(true).plantUml("note left: hi").focusFields(List.of("id"));
        assertThat(log.phase()).isEqualTo(TestPhase.ACTION);
        assertThat(log.noteOnRight()).isTrue();
        assertThat(log.plantUml()).isEqualTo("note left: hi");
        assertThat(log.focusFields()).containsExactly("id");
    }

    @Test
    void toBuilderCopiesCoreAndEnrichment() {
        var original = minimal().content("original").build();
        original.phase(TestPhase.SETUP).noteOnRight(true);

        var copy = original.toBuilder().content("modified").build();

        assertThat(copy.content()).isEqualTo("modified");
        assertThat(copy.testName()).isEqualTo("MyTest");
        assertThat(copy.traceId()).isEqualTo(original.traceId());
        // enrichment carried through
        assertThat(copy.phase()).isEqualTo(TestPhase.SETUP);
        assertThat(copy.noteOnRight()).isTrue();
    }

    @Test
    void customMethodPreservesVerb() {
        var log = minimal().method(Method.of("QUERY")).build();
        assertThat(log.method()).isInstanceOf(Method.Custom.class);
        assertThat(log.method().value()).isEqualTo("QUERY");
    }
}
