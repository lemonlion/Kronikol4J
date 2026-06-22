package io.kronikol.diagram.plantuml;

import io.kronikol.diagram.json.Json;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Formats GraphQL request bodies for diagram notes per the configured {@link GraphQlBodyFormat} — a
 * faithful port of the .NET {@code GraphQlBodyFormatter}. Returns {@code null} when the content is not a
 * GraphQL body or the format is {@link GraphQlBodyFormat#JSON}.
 *
 * <p>The {@code variables}/{@code extensions} sections are re-serialized with System.Text.Json's
 * indented <em>default</em> (HTML-safe) encoder — {@code " & ' + < > `}/control/non-ASCII escaped to
 * upper-case {@code \\uXXXX}, nulls kept — distinct from the relaxed encoder the note JSON body uses.
 */
public final class GraphQlBodyFormatter {

    private GraphQlBodyFormatter() {
    }

    public static String tryFormat(String content, GraphQlBodyFormat format) {
        if (format == GraphQlBodyFormat.JSON || content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.stripLeading();
        if (trimmed.isEmpty() || trimmed.charAt(0) != '{') {
            return null;
        }
        Object parsed;
        try {
            parsed = Json.parse(content);
        } catch (RuntimeException notJson) {
            return null;
        }
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("query") instanceof String rawQuery)) {
            return null;
        }
        if (GraphQlOperationDetector.tryExtractLabel(content) == null) { // verify it's really GraphQL
            return null;
        }

        String formattedQuery = GraphQlQueryFormatter.formatQuery(rawQuery);
        if (format == GraphQlBodyFormat.FORMATTED_QUERY_ONLY || format == GraphQlBodyFormat.FORMATTED) {
            return formattedQuery;
        }

        // FORMATTED_WITH_METADATA: append variables + extensions if present.
        String result = formattedQuery;
        String variables = formatJsonProperty(root, "variables");
        if (variables != null) {
            result += "\n\nvariables:\n" + variables;
        }
        String extensions = formatJsonProperty(root, "extensions");
        if (extensions != null) {
            result += "\n\nextensions:\n" + extensions;
        }
        return result;
    }

    private static String formatJsonProperty(Map<?, ?> root, String name) {
        if (!root.containsKey(name)) {
            return null;
        }
        Object value = root.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> m && m.isEmpty()) {
            return null; // .NET: empty object → omitted
        }
        StringBuilder sb = new StringBuilder();
        writeIndented(sb, value, 0);
        return sb.toString();
    }

    /** Indented serialization keeping nulls (System.Text.Json WriteIndented, default encoder). */
    private static void writeIndented(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            int n = map.size();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                appendIndent(sb, indent + 1);
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(": ");
                writeIndented(sb, e.getValue(), indent + 1);
                sb.append(++i < n ? ",\n" : "\n");
            }
            appendIndent(sb, indent);
            sb.append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                appendIndent(sb, indent + 1);
                writeIndented(sb, list.get(i), indent + 1);
                sb.append(i < list.size() - 1 ? ",\n" : "\n");
            }
            appendIndent(sb, indent);
            sb.append(']');
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Json.RawNumber rn) {
            sb.append(rn.literal());
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else {
            writeString(sb, value.toString());
        }
    }

    /** System.Text.Json default (HTML-safe) string escaping (mirrors {@code CompactJson.writeString}). */
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\u0022");
                case '&' -> sb.append("\\u0026");
                case '\'' -> sb.append("\\u0027");
                case '+' -> sb.append("\\u002B");
                case '<' -> sb.append("\\u003C");
                case '>' -> sb.append("\\u003E");
                case '`' -> sb.append("\\u0060");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\t' -> sb.append("\\t");
                case '\n' -> sb.append("\\n");
                case '\f' -> sb.append("\\f");
                case '\r' -> sb.append("\\r");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        sb.append("\\u").append(String.format(Locale.ROOT, "%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void appendIndent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }
}
