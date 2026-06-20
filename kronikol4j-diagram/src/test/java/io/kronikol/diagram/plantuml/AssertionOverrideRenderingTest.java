package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssertionOverrideRenderingTest {

    private static RequestResponseLog override(String fragment) {
        return RequestResponseLog.builder()
            .testName("T").testId("t")
            .method(Method.of("ASSERT")).uri(URI.create("assert://assertion/"))
            .serviceName("Assertion").callerName("Test")
            .type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .build()
            .plantUml(fragment);
    }

    @Test
    void plantUmlOverrideIsEmittedVerbatimWithoutDeclaringParticipants() {
        String fragment = "hnote across #d4edda\n✓ order is confirmed\nend note";
        String uml = PlantUmlCreator.create(List.of(override(fragment))).get(0).diagrams().get(0);

        assertThat(uml).contains(fragment);
        // an override declares no participant of its own
        assertThat(uml).doesNotContain("participant \"Assertion\"");
        assertThat(uml).startsWith("@startuml\n").endsWith("@enduml");
    }
}
