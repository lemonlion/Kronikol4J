package io.kronikol.diagram.plantuml;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.core.constants.DependencyCategories;
import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Verifies the caller-supplied note content processors fire at the right stages (the .NET pre/mid/post
 * {@code Func<string,string>} hooks). The functions are caller-supplied, so the test asserts hook
 * <em>placement</em>: pre transforms the raw content, mid the formatted content (before focus), post the
 * final note body.
 */
class NoteProcessorsTest {

    @Test
    void preMidPostFireAtTheRightStages() {
        NoteProcessors processors = new NoteProcessors(
            content -> content.replace("egg", "spam"), // requestPre — on the raw content
            formatted -> formatted + "\n<!--mid-->",    // requestMid — on the formatted (pre-focus) content
            note -> note + " <!--post-->",              // requestPost — on the final note body
            null, null, null);

        RequestResponseLog request = RequestResponseLog.builder()
            .testName("t").testId("t1").method(Method.Http.POST).uri(URI.create("http://svc/x"))
            .serviceName("Svc").callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(DependencyCategories.HTTP).content("{\"item\":\"egg\"}").build();

        String puml = PlantUmlCreator.create(List.of(request), DiagramOptions.colours(false, false), processors)
            .get(0).diagrams().get(0);

        assertThat(puml)
            .contains("\"item\": \"spam\"") // pre rewrote the raw content before pretty-printing
            .doesNotContain("egg")
            .contains("<!--mid-->")         // mid appended to the formatted content
            .contains("<!--post-->");       // post appended to the final note body
    }

    @Test
    void noneIsANoOp() {
        RequestResponseLog request = RequestResponseLog.builder()
            .testName("t").testId("t1").method(Method.Http.POST).uri(URI.create("http://svc/x"))
            .serviceName("Svc").callerName("Test").type(RequestResponseType.REQUEST)
            .traceId(UUID.randomUUID()).requestResponseId(UUID.randomUUID())
            .dependencyCategory(DependencyCategories.HTTP).content("{\"item\":\"egg\"}").build();

        String withNone = PlantUmlCreator.create(List.of(request),
            DiagramOptions.colours(false, false), NoteProcessors.NONE).get(0).diagrams().get(0);
        String without = PlantUmlCreator.create(List.of(request), false).get(0).diagrams().get(0);
        assertThat(withNone).isEqualTo(without);
    }
}
