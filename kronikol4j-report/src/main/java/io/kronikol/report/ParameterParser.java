package io.kronikol.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Port of the .NET {@code ParameterParser} string-based methods: detecting and formatting complex
 * parameter values from a C# record/class {@code ToString()} representation
 * ("{@code TypeName { Prop = Val, ... }}") or a generic-collection type ("{@code List`1[...]}").
 *
 * <p>Only the deterministic <em>string-based</em> path is ported — it is byte-stable across runtimes.
 * The .NET reflection-based path (rendering arbitrary {@code object} values via {@code PropertyInfo})
 * produces runtime-specific output (PascalCase property names, .NET type names) and is not
 * cross-runtime byte-parity-able, so it is intentionally not ported.
 */
final class ParameterParser {

    private static final Pattern GENERIC_TYPE_PATTERN =
        Pattern.compile("^(?:[\\w.]+\\.)?(\\w+)`\\d+\\[(.+)\\]$");

    private ParameterParser() {
    }

    /** Parses "{@code TypeName { Prop1 = Val1, Prop2 = Val2 }}" into ordered name→value pairs, or null. */
    static Map<String, String> tryParseRecordToString(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        int braceOpen = value.indexOf(" { ");
        if (braceOpen <= 0) {
            return null;
        }
        boolean isTruncated = !value.endsWith(" }");
        if (isTruncated && !isTruncationSuffix(value)) {
            return null;
        }
        int innerStart = braceOpen + 3;
        int innerEnd = isTruncated ? value.length() : value.length() - 2;
        if (innerEnd <= innerStart) {
            return null;
        }
        String inner = value.substring(innerStart, innerEnd).strip();
        if (inner.isEmpty()) {
            return null;
        }
        if (isTruncated) {
            inner = stripTrailingTruncation(inner);
        }

        Map<String, String> result = new LinkedHashMap<>();
        int pos = 0;
        while (pos < inner.length()) {
            while (pos < inner.length() && inner.charAt(pos) == ' ') {
                pos++;
            }
            if (pos >= inner.length()) {
                break;
            }
            int eqIdx = inner.indexOf(" = ", pos);
            if (eqIdx < 0) {
                String trimmedTail = inner.substring(pos).stripTrailing();
                if (trimmedTail.endsWith(" =")) {
                    String trailingPropName = trimmedTail.substring(0, trimmedTail.length() - 2).strip();
                    if (!trailingPropName.isEmpty()) {
                        result.put(trailingPropName, "null");
                    }
                    break;
                }
                if (isTruncated) {
                    break;
                }
                return null; // malformed
            }
            String propName = inner.substring(pos, eqIdx).strip();
            if (propName.isEmpty()) {
                return null;
            }
            pos = eqIdx + 3; // skip " = "

            String propValue;
            if (pos < inner.length() && inner.charAt(pos) == '"') {
                StringBuilder sb = new StringBuilder();
                pos++; // skip opening quote
                boolean closedQuote = false;
                while (pos < inner.length()) {
                    char c = inner.charAt(pos);
                    if (c == '\\' && pos + 1 < inner.length()) {
                        sb.append(inner.charAt(pos + 1));
                        pos += 2;
                        continue;
                    }
                    if (c == '"') {
                        pos++;
                        closedQuote = true;
                        break;
                    }
                    sb.append(c);
                    pos++;
                }
                propValue = sb.toString();
                if (!closedQuote && isTruncated) {
                    result.put(propName, propValue);
                    break;
                }
                while (pos < inner.length() && (inner.charAt(pos) == '·' || inner.charAt(pos) == '.')) {
                    pos++;
                }
            } else {
                int valueStart = pos;
                int braceDepth = 0;
                while (pos < inner.length()) {
                    char c = inner.charAt(pos);
                    if (c == '{') {
                        braceDepth++;
                    } else if (c == '}') {
                        braceDepth--;
                    } else if (c == ',' && braceDepth == 0) {
                        break;
                    }
                    pos++;
                }
                propValue = inner.substring(valueStart, pos).strip();
                propValue = stripTrailingTruncation(propValue);
                if (propValue.isEmpty()) {
                    propValue = "null";
                }
            }
            result.put(propName, propValue);

            if (pos < inner.length() && inner.charAt(pos) == ',') {
                pos++;
                while (pos < inner.length() && inner.charAt(pos) == ' ') {
                    pos++;
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    static boolean isComplexObjectString(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        if (tryParseRecordToString(value) != null) {
            return true;
        }
        return GENERIC_TYPE_PATTERN.matcher(value).matches();
    }

    /** Small enough to render inline: a record with fewer than 5 simple (non-nested) fields. */
    static boolean isSmallComplexValue(String value) {
        Map<String, String> props = tryParseRecordToString(value);
        if (props == null || props.isEmpty() || props.size() >= 5) {
            return false;
        }
        for (String v : props.values()) {
            if (tryParseRecordToString(v) != null) {
                return false;
            }
        }
        return true;
    }

    /** "{@code TypeName { Name = Val, Flour = Plain }}" → "{@code { Name: Val, Flour: Plain }}", or null. */
    static String formatComplexValueInline(String value) {
        Map<String, String> props = tryParseRecordToString(value);
        if (props == null || props.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> kv : props.entrySet()) {
            parts.add(kv.getKey() + ": " + kv.getValue());
        }
        return "{ " + String.join(", ", parts) + " }";
    }

    /** Pretty-printed JSON of the record properties (without the type name), or null. */
    static String formatComplexValueAsJson(String value) {
        Map<String, String> props = tryParseRecordToString(value);
        if (props == null || props.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        List<Map.Entry<String, String>> entries = new ArrayList<>(props.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            sb.append("  \"").append(entries.get(i).getKey()).append("\": ");
            sb.append(formatJsonValue(entries.get(i).getValue()));
            if (i < entries.size() - 1) {
                sb.append(',');
            }
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    private static String formatJsonValue(String val) {
        if (val.equals("null")) {
            return "null";
        }
        if (val.equalsIgnoreCase("true")) {
            return "true";
        }
        if (val.equalsIgnoreCase("false")) {
            return "false";
        }
        if (isNumeric(val)) {
            return val;
        }
        return "\"" + val.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Mirrors .NET {@code double.TryParse(val, NumberStyles.Any, InvariantCulture)} for the JSON test. */
    private static boolean isNumeric(String val) {
        if (val.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(val.strip());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isTruncationSuffix(String value) {
        int i = value.length() - 1;
        while (i >= 0 && (value.charAt(i) == '.' || value.charAt(i) == '·')) {
            i--;
        }
        return i < value.length() - 1;
    }

    private static String stripTrailingTruncation(String value) {
        int i = value.length() - 1;
        while (i >= 0 && (value.charAt(i) == '.' || value.charAt(i) == '·')) {
            i--;
        }
        return i < value.length() - 1 ? value.substring(0, i + 1).stripTrailing() : value;
    }
}
