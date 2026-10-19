package io.kronikol.report.data;

import io.kronikol.diagram.json.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * {@code TestRunReport.krkl}, the report-data file compressed whole (the .NET {@code CompressTestRunReport};
 * plans/REPORT_FORMAT_PLAN.md R6): the JSON written compact, {@code formatVersion} 3 first, every key whose value is
 * {@code null}, {@code []} or <code>{}</code> left out unless the schema requires it where it stands, and the whole
 * gzipped with {@code java.util.zip}, so the report module takes no dependency. Kronikol's readers, {@code kronikol}
 * 4.30.0 and later, tell gzip from the file's first bytes, whatever its name.
 */
public final class CompressedReportFile {

    /** The file's name beside the HTML report. */
    public static final String FILE_NAME = "TestRunReport.krkl";

    /** The shape of the JSON inside: keys that hold nothing may be absent, and no payload is wrapped. */
    public static final int FORMAT_VERSION = 3;

    private static final class Schema {
        static final ReportRequirements.Node ROOT = ReportRequirements.fromSchema(Json.parse(ReportDataSchema.jsonSchema()));
    }

    private CompressedReportFile() {
    }

    /** Writes {@code json}, the report data as {@link ReportDataSerializer#toJson} writes it, to {@code target}: into a
     *  file beside it first, then moved over it, so a reader never sees half a file. */
    public static void write(Path target, String json) throws IOException {
        byte[] bytes = encode(json);
        Path directory = target.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        // A file this process may not write stays as it is, as Files.writeString leaves a read-only TestRunReport.json: a
        // rename needs only the directory, so it would replace the file on Linux and macOS (.NET 4.32.1).
        if (Files.exists(target) && !Files.isWritable(target)) {
            throw new AccessDeniedException(target.toString(), null, "the file is not writable");
        }
        Path temporary = Files.createTempFile(directory, target.getFileName().toString() + ".", ".tmp");
        try {
            Files.write(temporary, bytes);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** The file's bytes for {@code json}: the gzip of {@link #compact(String)}. */
    public static byte[] encode(String json) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(compact(json).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /** The JSON the file holds for {@code json}, before it is compressed. */
    public static String compact(String json) {
        return compact(json, Schema.ROOT);
    }

    /** {@link #compact(String)} against another schema: for tests of the rules themselves. */
    static String compact(String json, String schema) {
        return compact(json, ReportRequirements.fromSchema(Json.parse(schema)));
    }

    private static String compact(String json, ReportRequirements.Node root) {
        Object document = prune(Json.parse(json), root);
        if (!(document instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("the report data is not a JSON object");
        }
        // formatVersion first, as .NET writes it, so a reader can check the contract before anything that depends on it.
        Map<String, Object> versioned = new LinkedHashMap<>();
        versioned.put("formatVersion", Json.number(FORMAT_VERSION));
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!"formatVersion".equals(entry.getKey())) {
                versioned.put((String) entry.getKey(), entry.getValue());
            }
        }
        StringBuilder out = new StringBuilder(json.length() / 2);
        writeValue(versioned, out);
        return out.toString();
    }

    /** Leaves out, bottom-up, every key whose value is null or empty that {@code node} does not require. Array
     *  elements are always kept, and an open part is kept exactly as written. */
    private static Object prune(Object value, ReportRequirements.Node node) {
        if (node != null && node.isOpen()) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> kept = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = (String) entry.getKey();
                Object child = prune(entry.getValue(), node == null ? null : node.property(key));
                if (isEmpty(child) && (node == null || !node.requires(key))) {
                    continue;
                }
                kept.put(key, child);
            }
            return kept;
        }
        if (value instanceof List<?> list) {
            ReportRequirements.Node items = node == null ? null : node.items();
            List<Object> kept = new ArrayList<>(list.size());
            for (Object element : list) {
                kept.add(prune(element, items));
            }
            return kept;
        }
        return value;
    }

    private static boolean isEmpty(Object value) {
        return value == null || value instanceof Map<?, ?> map && map.isEmpty() || value instanceof List<?> list && list.isEmpty();
    }

    private static void writeValue(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            writeString(s, out);
        } else if (value instanceof Boolean b) {
            out.append(b.booleanValue() ? "true" : "false");
        } else if (value instanceof Json.RawNumber number) {
            out.append(number.literal());
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString((String) entry.getKey(), out);
                out.append(':');
                writeValue(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                writeValue(list.get(i), out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("not a JSON value: " + value.getClass().getName());
        }
    }

    /** The escapes JSON needs and no others, as .NET's relaxed encoder writes the data file: a reader decodes the same
     *  text either way. */
    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
