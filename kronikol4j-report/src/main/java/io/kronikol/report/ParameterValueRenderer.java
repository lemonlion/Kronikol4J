package io.kronikol.report;

import io.kronikol.report.html.HtmlEscaper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of the <em>string-based</em> cell renderers of the .NET {@code ParameterValueRenderer} — the
 * R3 (sub-table) / R4 (expandable) rendering of a parameter value that arrives as a C# record
 * {@code ToString()} string ("{@code TypeName { Prop = Val, ... }}").
 *
 * <p>The .NET reflection-based path (rendering a live {@code object} value via {@code PropertyInfo}:
 * {@code RenderSubTable(object)}, {@code RenderExpandable(object)}, {@code GenerateHighlightedJson(object)},
 * {@code IsSmallComplexObject}, {@code IsComplexValue}, {@code TryGetFlattenableProperties}, the
 * {@code FlattenTo*} helpers) produces runtime-specific output (PascalCase property names, .NET type
 * names) and is not cross-runtime byte-parity-able, so only the deterministic string-based subset is
 * ported here. It mirrors .NET {@code ParameterValueRenderer.TryRenderFromParsedString} and the
 * {@code *FromParsed} helpers exactly.
 */
final class ParameterValueRenderer {

    private ParameterValueRenderer() {
    }

    /**
     * Attempts string-based R3/R4 rendering for a cell value when no raw object is available.
     * Returns true and appends HTML if the string matches a record {@code ToString()} pattern;
     * R3 (sub-table) is used for ≤ {@code maxSubTableProperties} properties, R4 (expandable) for more.
     */
    static boolean tryRenderFromParsedString(StringBuilder body, String value) {
        return tryRenderFromParsedString(body, value, 5);
    }

    static boolean tryRenderFromParsedString(StringBuilder body, String value, int maxSubTableProperties) {
        try {
            Map<String, String> parsed = ParameterParser.tryParseRecordToString(value);
            if (parsed == null) {
                return false;
            }
            if (parsed.size() <= maxSubTableProperties) {
                renderSubTableFromParsed(body, parsed);
                return true;
            }
            renderExpandableFromParsed(body, value, parsed);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * Renders a sub-table (R3) from parsed string key-value pairs. Recursively renders nested record
     * values and cleans up collection type names.
     */
    static void renderSubTableFromParsed(StringBuilder body, Map<String, String> parsed) {
        body.append("<table class=\"cell-subtable\">");
        for (Map.Entry<String, String> kvp : parsed.entrySet()) {
            body.append("<tr><th>").append(HtmlEscaper.encode(kvp.getKey())).append("</th><td>");
            renderParsedValue(body, kvp.getValue());
            body.append("</td></tr>");
        }
        body.append("</table>");
    }

    /**
     * Renders a single parsed value intelligently: nested records become sub-tables, collection type
     * names become readable labels, and scalars are HTML-encoded.
     */
    static void renderParsedValue(StringBuilder body, String value) {
        Map<String, String> nestedParsed = ParameterParser.tryParseRecordToString(value);
        if (nestedParsed != null && !nestedParsed.isEmpty()) {
            renderSubTableFromParsed(body, nestedParsed);
            return;
        }
        String cleaned = tryCleanCollectionTypeName(value);
        if (cleaned != null) {
            body.append("<span class=\"mono\">").append(HtmlEscaper.encode(cleaned)).append("</span>");
            return;
        }
        body.append(HtmlEscaper.encode(value));
    }

    /**
     * Detects and cleans .NET collection type names like
     * "{@code System.Collections.Generic.List`1[Namespace.Type]}" into "{@code List<Type>}".
     * Returns null if the value is not a collection type name.
     */
    static String tryCleanCollectionTypeName(String value) {
        if (!value.startsWith("System.Collections.") && !value.startsWith("System.Linq.")) {
            return null;
        }
        int backtickIdx = value.indexOf('`');
        if (backtickIdx < 0) {
            int lastDot = value.lastIndexOf('.');
            return lastDot >= 0 ? value.substring(lastDot + 1) : value;
        }
        int nameStart = value.lastIndexOf('.', backtickIdx - 1) + 1;
        String collectionName = value.substring(nameStart, backtickIdx);

        int bracketStart = value.indexOf('[', backtickIdx);
        int bracketEnd = value.lastIndexOf(']');
        if (bracketStart < 0 || bracketEnd <= bracketStart) {
            return collectionName;
        }
        String fullArgType = value.substring(bracketStart + 1, bracketEnd);
        int argLastDot = fullArgType.lastIndexOf('.');
        String simpleArgType = argLastDot >= 0 ? fullArgType.substring(argLastDot + 1) : fullArgType;
        int plusIdx = simpleArgType.lastIndexOf('+');
        if (plusIdx >= 0) {
            simpleArgType = simpleArgType.substring(plusIdx + 1);
        }
        return "List<" + simpleArgType + ">";
    }

    /** Renders an expandable details/summary (R4) from parsed string key-value pairs. */
    static void renderExpandableFromParsed(StringBuilder body, String originalValue, Map<String, String> parsed) {
        String preview = generatePreviewFromParsed(originalValue, parsed);
        if (preview.length() > 300) {
            preview = preview.substring(0, 297) + "...";
        }
        String jsonBody = generateHighlightedJsonFromParsed(parsed);
        if (jsonBody.length() > 10000) {
            jsonBody = jsonBody.substring(0, 10000) + "<span class=\"prop-val\">… (truncated)</span>";
        }
        body.append("<details class=\"param-expand\">");
        body.append("<summary>").append(HtmlEscaper.encode(preview)).append("</summary>");
        body.append("<div class=\"expand-body\">").append(jsonBody).append("</div>");
        body.append("</details>");
    }

    /**
     * Generates a short preview for the parsed record (shows up to 3 properties). Cleans up nested
     * record values and collection type names in the preview.
     */
    static String generatePreviewFromParsed(String originalValue, Map<String, String> parsed) {
        int braceIdx = originalValue.indexOf(" { ");
        String typeName = braceIdx >= 0 ? originalValue.substring(0, braceIdx) : "Object";

        List<String> previewParts = new ArrayList<>();
        int taken = 0;
        for (Map.Entry<String, String> kvp : parsed.entrySet()) {
            if (taken >= 3) {
                break;
            }
            String val = tryCleanCollectionTypeName(kvp.getValue());
            if (val == null) {
                val = kvp.getValue();
            }
            int nestedBrace = val.indexOf(" { ");
            if (nestedBrace > 0) {
                val = val.substring(0, nestedBrace) + " {...}";
            }
            previewParts.add(kvp.getKey() + ": " + val);
            taken++;
        }
        String suffix = parsed.size() > 3 ? ", ..." : "";
        return typeName + " { " + String.join(", ", previewParts) + suffix + " }";
    }

    /**
     * Generates JSON-like highlighted HTML from parsed string key-value pairs. Recursively renders
     * nested record values.
     */
    static String generateHighlightedJsonFromParsed(Map<String, String> parsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        List<Map.Entry<String, String>> entries = new ArrayList<>(parsed.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            String key = entries.get(i).getKey();
            String value = entries.get(i).getValue();
            sb.append("  <span class=\"prop-key\">\"").append(HtmlEscaper.encode(key)).append("\"</span>: ");

            Map<String, String> nestedParsed = ParameterParser.tryParseRecordToString(value);
            if (nestedParsed != null && !nestedParsed.isEmpty()) {
                sb.append(generateHighlightedJsonFromParsed(nestedParsed));
            } else {
                String cleaned = tryCleanCollectionTypeName(value);
                sb.append("<span class=\"prop-val\">").append(HtmlEscaper.encode(cleaned != null ? cleaned : value))
                    .append("</span>");
            }
            if (i < entries.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }
}
