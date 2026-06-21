package io.kronikol.report.flow;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A minimal compact JSON writer byte-identical to .NET {@code System.Text.Json.JsonSerializer.Serialize}
 * with {@code WriteIndented = false} and the default (HTML-safe) encoder — no whitespace, insertion-order
 * object keys, integers without a decimal point, doubles as shortest decimal with trailing zeros
 * stripped, and {@code " & ' + < > `} / control / non-ASCII escaped to upper-case {@code \\uXXXX}.
 *
 * <p>Supports the value shapes the internal-flow segment data uses: {@link String}, {@link Integer}/
 * {@link Long}, {@link Double}, {@link Boolean}, {@code null}, {@link Map} (object), and {@link List} /
 * {@code Object[]} (array).
 */
public final class CompactJson {

    private CompactJson() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    static void writeValue(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof Integer i) {
            sb.append(i.intValue());
        } else if (v instanceof Long l) {
            sb.append(l.longValue());
        } else if (v instanceof Double d) {
            sb.append(formatNumber(d));
        } else if (v instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (v instanceof Map<?, ?> m) {
            writeObject(sb, m);
        } else if (v instanceof List<?> l) {
            writeArray(sb, l);
        } else if (v instanceof Object[] arr) {
            writeArray(sb, List.of(arr));
        } else {
            writeString(sb, v.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, String.valueOf(e.getKey()));
            sb.append(':');
            writeValue(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            writeValue(sb, list.get(i));
        }
        sb.append(']');
    }

    /** Integer-valued doubles without a decimal point, else shortest decimal with trailing zeros stripped. */
    static String formatNumber(double r) {
        if (r == Math.rint(r) && !Double.isInfinite(r)) {
            return Long.toString((long) r);
        }
        return BigDecimal.valueOf(r).stripTrailingZeros().toPlainString();
    }

    /** Escapes a string exactly like System.Text.Json's default (HTML-safe) encoder. */
    static void writeString(StringBuilder sb, String s) {
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
}
