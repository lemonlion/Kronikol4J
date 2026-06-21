package io.kronikol.report.flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Byte-parity for {@link InternalFlowHtmlGenerator}'s {@code window.__iflowSegments} script against the
 * real .NET {@code GenerateSegmentDataScript}. Uses the CALL_TREE style so the script contains no gzip
 * {@code data-plantuml-z} and is wholly byte-comparable.
 */
class InternalFlowHtmlGeneratorTest {

    private static final Instant T0 = Instant.parse("2024-01-15T10:00:00Z");

    @Test
    void generateSegmentDataScript_callTreeWithFlame_isByteForByteIdenticalToDotNet() {
        InternalFlowSegment segment = new InternalFlowSegment("t1", List.of(
            new InternalFlowSpan("1", null, "Kronikol.Request", null, "GET /orders", T0, 100.0),
            new InternalFlowSpan("2", "1", "OrderService", null, "LoadOrder", T0.plusMillis(10), 40.0),
            new InternalFlowSpan("3", "2", "Database", null, "SELECT", T0.plusMillis(15), 20.0),
            new InternalFlowSpan("4", "1", "OrderService", null, "Validate", T0.plusMillis(60), 30.0)),
            "00000000-0000-0000-0000-000000000001", "request");
        Map<String, InternalFlowSegment> segments =
            Map.of("iflow-00000000-0000-0000-0000-000000000001", segment);

        String script = InternalFlowHtmlGenerator.generateSegmentDataScript(
            segments, InternalFlowDiagramStyle.CALL_TREE, true,
            InternalFlowFlameChartPosition.BEHIND_WITH_TOGGLE, InternalFlowNoDataBehavior.HIDE_LINK,
            InternalFlowSpanGranularity.AUTO_INSTRUMENTATION, null, 0);

        assertEquals(readFixture("iflow-segmentdata.txt"), script,
            "window.__iflowSegments script differs from the .NET GenerateSegmentDataScript golden");
    }

    @Test
    void configScript_matchesDotNet() {
        assertEquals("<script>window.__iflowConfig = { hasDataBehavior: 'showLinkOnHover' };</script>",
            InternalFlowHtmlGenerator.getInternalFlowConfigScript(
                InternalFlowHasDataBehavior.SHOW_LINK_ON_HOVER));
        assertEquals("<script>window.__iflowConfig = { hasDataBehavior: 'showLink' };</script>",
            InternalFlowHtmlGenerator.getInternalFlowConfigScript(InternalFlowHasDataBehavior.SHOW_LINK));
    }

    private static String readFixture(String name) {
        try (InputStream in = InternalFlowHtmlGeneratorTest.class.getResourceAsStream("/parity/" + name)) {
            assertTrue(in != null, "fixture /parity/" + name + " not found on test classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
