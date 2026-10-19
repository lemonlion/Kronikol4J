package io.kronikol.report.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.kronikol.diagram.json.Json;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@code TestRunReport.krkl} as Kronikol4J writes it (plans/REPORT_FORMAT_PLAN.md R6): the JSON the .NET writer would put
 * in the file, compared as content, and gzip around it, which {@code kronikol} 4.30.0 and later reads.
 */
class CompressedReportFileTest {

    /** A schema that requires keys at three depths, through a reference, with an open part and a map. */
    private static final String SCHEMA = """
        {
          "type": "object",
          "required": ["features", "suite"],
          "properties": {
            "suite": {"type": ["string", "null"]},
            "note": {"type": ["string", "null"]},
            "features": {"type": "array", "items": {"$ref": "#/$defs/feature"}},
            "segments": {"type": "object"},
            "labels": {"type": "object", "additionalProperties": {"type": "array", "items": {"type": "string"}}}
          },
          "$defs": {
            "feature": {
              "type": "object",
              "required": ["name", "tags"],
              "properties": {
                "name": {"type": ["string", "null"]},
                "tags": {"type": "array", "items": {"type": "string"}},
                "extra": {"type": ["object", "null"], "properties": {"a": {"type": ["string", "null"]}}},
                "children": {"type": "array", "items": {"$ref": "#/$defs/feature"}}
              }
            }
          }
        }
        """;

    @Test
    void leavesOutNullAndEmptyKeysTheSchemaDoesNotRequire() {
        String json = """
            {"suite": null, "note": null, "features": [], "labels": {}, "segments": {}}
            """;

        assertThat(CompressedReportFile.compact(json, SCHEMA))
            .isEqualTo("{\"formatVersion\":3,\"suite\":null,\"features\":[]}");
    }

    @Test
    void keepsARequiredKeyAtEveryDepthThroughAReference() {
        String json = """
            {"suite": "s", "features": [{"name": null, "tags": [], "extra": null,
              "children": [{"name": "child", "tags": [], "extra": {"a": null}}]}]}
            """;

        // extra.a goes, then extra, now empty, goes with it; the nested feature keeps its required name and tags.
        assertThat(CompressedReportFile.compact(json, SCHEMA)).isEqualTo(
            "{\"formatVersion\":3,\"suite\":\"s\",\"features\":[{\"name\":null,\"tags\":[],"
                + "\"children\":[{\"name\":\"child\",\"tags\":[]}]}]}");
    }

    @Test
    void keepsArrayElementsAndAnOpenPartAsWritten() {
        String json = """
            {"suite": "s", "features": [{"name": "f", "tags": ["", ""]}],
             "segments": {"a": null, "b": [], "c": {"d": {}}}, "labels": {"x": [], "y": ["t"]}}
            """;

        assertThat(CompressedReportFile.compact(json, SCHEMA)).isEqualTo(
            "{\"formatVersion\":3,\"suite\":\"s\",\"features\":[{\"name\":\"f\",\"tags\":[\"\",\"\"]}],"
                + "\"segments\":{\"a\":null,\"b\":[],\"c\":{\"d\":{}}},\"labels\":{\"y\":[\"t\"]}}");
    }

    @Test
    void writesOnlyTheEscapesJsonNeedsAndKeepsNumbersAsWritten() {
        String json = "{\"suite\": \"<a & b> \\u00e9 \\\" \\\\ \\n \\u0001\", \"features\": [], \"note\": 1.0}";

        assertThat(CompressedReportFile.compact(json, SCHEMA))
            .isEqualTo("{\"formatVersion\":3,\"suite\":\"<a & b> \u00e9 \\\" \\\\ \\n \\u0001\",\"features\":[],\"note\":1.0}");
    }

    @Test
    void theFileIsTheGzipOfTheCompactJson(@TempDir Path dir) throws IOException {
        String json = "{\"startTime\": \"2026-01-01T00:00:00Z\", \"endTime\": \"2026-01-01T00:01:00Z\", \"features\": []}";
        Path target = dir.resolve(CompressedReportFile.FILE_NAME);

        CompressedReportFile.write(target, json);

        byte[] bytes = Files.readAllBytes(target);
        assertThat(bytes[0] & 0xff).isEqualTo(0x1f);
        assertThat(bytes[1] & 0xff).isEqualTo(0x8b);
        assertThat(gunzip(bytes)).isEqualTo(CompressedReportFile.compact(json));
        try (var listing = Files.list(dir)) {
            assertThat(listing.map(p -> p.getFileName().toString())).containsExactly(CompressedReportFile.FILE_NAME);
        }
    }

    @Test
    void aFileThisProcessMayNotWriteIsLeftAsItIs(@TempDir Path dir) throws IOException {
        // As Files.writeString leaves a read-only TestRunReport.json: a rename needs only the directory, so it would
        // replace the file on Linux and macOS (.NET 4.32.1).
        Path target = dir.resolve(CompressedReportFile.FILE_NAME);
        Files.writeString(target, "an earlier run");
        assumeTrue(target.toFile().setWritable(false) && !Files.isWritable(target),
            "this process writes read-only files (root), so no file here is one it may not write");
        try {
            AccessDeniedException refused = assertThrows(AccessDeniedException.class,
                () -> CompressedReportFile.write(target, "{\"features\": []}"));
            assertThat(refused.getFile()).isEqualTo(target.toString());
            assertThat(Files.readString(target)).isEqualTo("an earlier run");
            try (var listing = Files.list(dir)) {
                assertThat(listing.map(p -> p.getFileName().toString())).containsExactly(CompressedReportFile.FILE_NAME);
            }
        } finally {
            target.toFile().setWritable(true);
        }
    }

    @Test
    void theReportSchemaKeepsWhatItRequiresInARealReport() {
        // The report schema's own root requirements: startTime, endTime and features, even when features is empty.
        String compact = CompressedReportFile.compact(
            "{\"kronikolVersion\": null, \"startTime\": \"a\", \"endTime\": \"b\", \"features\": []}");

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) Json.parse(compact);
        assertThat(root.keySet()).containsExactly("formatVersion", "startTime", "endTime", "features");
        assertThat((List<?>) root.get("features")).isEmpty();
    }

    private static String gunzip(byte[] bytes) throws IOException {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
