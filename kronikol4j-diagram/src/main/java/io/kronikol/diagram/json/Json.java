package io.kronikol.diagram.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny, dependency-free JSON parser + <em>canonical</em> pretty-printer used to format JSON note
 * bodies in diagrams. Hand-rolled deliberately (plan §6.4): it reproduces the exact .NET behaviour
 * rather than relying on a library's defaults —
 * <ul>
 *   <li>input/document key order is preserved (never sorted),</li>
 *   <li>{@code null} object-properties are stripped (null array elements kept),</li>
 *   <li>2-space indent, {@code \n} newlines,</li>
 *   <li>{@code UnsafeRelaxedJsonEscaping}: only {@code " \\} and control chars are escaped —
 *       {@code < > & +} and non-ASCII pass through.</li>
 * </ul>
 */
public final class Json {

    private Json() {
    }

    /** Pretty-prints {@code text} canonically if it is valid JSON; otherwise returns {@code null}. */
    public static String tryPrettyPrint(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            Parser parser = new Parser(text);
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (!parser.atEnd()) {
                return null; // trailing content -> not a single clean JSON document
            }
            StringBuilder sb = new StringBuilder(text.length() + 16);
            write(value, sb, 0);
            return sb.toString();
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    /** A number token preserved verbatim so {@code 1.0} never becomes {@code 1} (parity). */
    public record RawNumber(String literal) {
    }

    /** Parses a complete JSON document into ordered maps / lists / String / Boolean / RawNumber / null.
     *  @throws IllegalArgumentException if the text is not a single valid JSON document. */
    public static Object parse(String text) {
        try {
            Parser parser = new Parser(text);
            Object value = parser.parseValue();
            parser.skipWhitespace();
            if (!parser.atEnd()) {
                throw new IllegalArgumentException("trailing content after JSON document");
            }
            return value;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("invalid JSON: " + e.getMessage(), e);
        }
    }

    /** Serializes a value (Map/List/String/Boolean/RawNumber/null) using the canonical format. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    /** Wraps a numeric literal for {@link #write}. */
    public static RawNumber number(long value) {
        return new RawNumber(Long.toString(value));
    }

    private static void write(Object value, StringBuilder sb, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof Map<?, ?> map) {
            writeObject(map, sb, indent);
        } else if (value instanceof List<?> list) {
            writeArray(list, sb, indent);
        } else if (value instanceof String s) {
            writeString(s, sb);
        } else if (value instanceof RawNumber n) {
            sb.append(n.literal());
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue() ? "true" : "false");
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder sb, int indent) {
        // Strip null-valued properties (parity with WriteElementWithoutNulls).
        List<Map.Entry<?, ?>> entries = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getValue() != null) {
                entries.add(e);
            }
        }
        if (entries.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        for (int i = 0; i < entries.size(); i++) {
            indent(sb, indent + 1);
            writeString(String.valueOf(entries.get(i).getKey()), sb);
            sb.append(": ");
            write(entries.get(i).getValue(), sb, indent + 1);
            sb.append(i < entries.size() - 1 ? ",\n" : "\n");
        }
        indent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder sb, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            indent(sb, indent + 1);
            write(list.get(i), sb, indent + 1); // null elements are kept
            sb.append(i < list.size() - 1 ? ",\n" : "\n");
        }
        indent(sb, indent);
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c); // UnsafeRelaxed: < > & + and non-ASCII pass through
                    }
                }
            }
        }
        sb.append('"');
    }

    private static void indent(StringBuilder sb, int level) {
        sb.append("  ".repeat(level));
    }

    /** Minimal recursive-descent JSON parser producing ordered maps / lists. */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return i >= s.length();
        }

        void skipWhitespace() {
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    i++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalStateException("unexpected end");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> parseNumber();
            };
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (peek() == '}') {
                i++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                map.put(key, parseValue());
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or }");
                }
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (peek() == ']') {
                i++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new IllegalStateException("expected , or ]");
                }
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = next();
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new IllegalStateException("bad escape");
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Object parseBoolean() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw new IllegalStateException("bad literal");
        }

        private Object parseNull() {
            if (s.startsWith("null", i)) {
                i += 4;
                return null;
            }
            throw new IllegalStateException("bad literal");
        }

        private RawNumber parseNumber() {
            int start = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            if (i == start) {
                throw new IllegalStateException("invalid token at " + i);
            }
            return new RawNumber(s.substring(start, i));
        }

        private char peek() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalStateException("unexpected end");
            }
            return s.charAt(i);
        }

        private char next() {
            if (atEnd()) {
                throw new IllegalStateException("unexpected end");
            }
            return s.charAt(i++);
        }

        private void expect(char c) {
            if (next() != c) {
                throw new IllegalStateException("expected " + c);
            }
        }
    }
}
