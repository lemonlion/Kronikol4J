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

    /**
     * Parses parameter name-value pairs from a test display name. Supports
     * "{@code Method(name: val, name2: val2)}" (named), "{@code Method(val1, val2)}" (positional),
     * and "{@code Scenario [name: val]}" (bracketed). Returns null if no params or input is empty.
     */
    static Map<String, String> parse(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }
        // Bracketed format first: "Name [key: val, ...]" or "Name [k1: v1] [k2: v2]".
        int spBracket = displayName.lastIndexOf(" [");
        if (spBracket >= 0 && displayName.endsWith("]")) {
            Map<String, String> allParams = new LinkedHashMap<>();
            String remaining = displayName;
            while (true) {
                if (remaining.length() < 3 || remaining.charAt(remaining.length() - 1) != ']') {
                    break;
                }
                int matchingOpen = findMatchingOpenBracket(remaining);
                if (matchingOpen < 1 || remaining.charAt(matchingOpen - 1) != ' ') {
                    break;
                }
                String inner = remaining.substring(matchingOpen + 1, remaining.length() - 1).strip();
                if (inner.isEmpty()) {
                    break;
                }
                Map<String, String> result = parseParams(inner);
                if (result.isEmpty()) {
                    break;
                }
                for (Map.Entry<String, String> kv : result.entrySet()) {
                    allParams.putIfAbsent(kv.getKey(), kv.getValue());
                }
                remaining = remaining.substring(0, matchingOpen - 1).stripTrailing();
            }
            if (!allParams.isEmpty()) {
                return allParams;
            }
        }
        // Parens format: "Method(params...)".
        int parenStart = findOpenParen(displayName);
        if (parenStart < 0 || !displayName.endsWith(")")) {
            return null;
        }
        String parenInner = displayName.substring(parenStart + 1, displayName.length() - 1).strip();
        if (parenInner.isEmpty()) {
            return null;
        }
        Map<String, String> parsed = parseParams(parenInner);
        return parsed.isEmpty() ? null : parsed;
    }

    /**
     * Extracts the base name (without the parameter suffix) from a display name: strips a trailing
     * "{@code (params)}" or one-or-more " {@code [params]}" groups. Returns null for empty input.
     */
    static String extractBaseName(String displayName) {
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }
        String current = displayName;
        while (true) {
            if (current.length() < 3 || current.charAt(current.length() - 1) != ']') {
                break;
            }
            int matchingOpen = findMatchingOpenBracket(current);
            if (matchingOpen < 1 || current.charAt(matchingOpen - 1) != ' ') {
                break;
            }
            current = current.substring(0, matchingOpen - 1).stripTrailing();
        }
        if (current.length() < displayName.length()) {
            return current;
        }
        int parenStart = findOpenParen(displayName);
        if (parenStart >= 0 && displayName.endsWith(")")) {
            return displayName.substring(0, parenStart).stripTrailing();
        }
        return displayName;
    }

    /** Index of the '[' matching the trailing ']' (considering nesting), or -1. */
    private static int findMatchingOpenBracket(String span) {
        int depth = 0;
        for (int i = span.length() - 1; i >= 0; i--) {
            if (span.charAt(i) == ']') {
                depth++;
            } else if (span.charAt(i) == '[') {
                depth--;
            }
            if (depth == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the first '(' not inside a quoted string, or -1. */
    private static int findOpenParen(String s) {
        boolean inQuote = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (c == '(' && !inQuote) {
                return i;
            }
        }
        return -1;
    }

    private static Map<String, String> parseParams(String inner) {
        Map<String, String> result = new LinkedHashMap<>();
        List<String> tokens = splitParams(inner);
        int positionalIndex = 0;
        for (String token : tokens) {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            int colonIdx = findColon(trimmed);
            if (colonIdx > 0) {
                String key = trimmed.substring(0, colonIdx).strip();
                String value = stripQuotes(trimmed.substring(colonIdx + 1).strip());
                result.put(key, value);
            } else {
                result.put("arg" + positionalIndex, stripQuotes(trimmed));
            }
            positionalIndex++;
        }
        return result;
    }

    /** Index of the first ':' not inside quotes/parens/braces/brackets, or -1. */
    private static int findColon(String s) {
        boolean inQuote = false;
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth--;
                } else if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                } else if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    bracketDepth--;
                } else if (c == ':' && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitParams(String inner) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '"' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
            }
            if (!inQuote) {
                if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth--;
                } else if (c == '{') {
                    braceDepth++;
                } else if (c == '}') {
                    braceDepth--;
                } else if (c == '[') {
                    bracketDepth++;
                } else if (c == ']') {
                    bracketDepth--;
                } else if (c == ',' && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
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
