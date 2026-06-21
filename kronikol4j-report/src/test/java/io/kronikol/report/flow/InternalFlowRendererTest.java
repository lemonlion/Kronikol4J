package io.kronikol.report.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Byte-parity for {@link InternalFlowRenderer}'s activity-diagram rendering against the raw PlantUML
 * captured from the real .NET {@code InternalFlowRenderer.RenderActivityDiagramBatched}. The fixture
 * keeps native CRLF line endings (see {@code .gitattributes}), since that is what the report gzips
 * into {@code puml-data} before its {@code ReplaceLineEndings("\n")}.
 */
class InternalFlowRendererTest {

    private static final Instant T0 = Instant.parse("2024-01-15T10:00:00Z");

    @Test
    void renderActivityDiagram_nestedSpanTree_isByteForByteIdenticalToDotNet() {
        // GET /orders (root) → LoadOrder → SELECT, then Validate (sibling of LoadOrder).
        InternalFlowSegment segment = new InternalFlowSegment("t1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /orders", T0, 100.0),
            new InternalFlowSpan("2", "1", "OrderService", null, "LoadOrder", T0.plusMillis(10), 40.0),
            new InternalFlowSpan("3", "2", "Database", null, "SELECT", T0.plusMillis(15), 20.0),
            new InternalFlowSpan("4", "1", "OrderService", null, "Validate", T0.plusMillis(60), 30.0)));

        List<String> batches = InternalFlowRenderer.renderActivityDiagramBatched(segment, 100);

        assertEquals(1, batches.size(), "a sub-100-span segment renders as a single diagram");
        assertEquals(readFixture("iflow-activity.txt"), batches.get(0),
            "activity-diagram PlantUML differs from the .NET golden (note CRLF)");
    }

    private static String readFixture(String name) {
        try (InputStream in = InternalFlowRendererTest.class.getResourceAsStream("/parity/" + name)) {
            assertTrue(in != null, "fixture /parity/" + name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
