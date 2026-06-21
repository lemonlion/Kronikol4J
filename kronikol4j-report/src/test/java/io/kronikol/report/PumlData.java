package io.kronikol.report;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Test helper: extracts the {@code puml-data} JSON island from a rendered report and base64-decodes +
 * gunzips each value back to its raw PlantUML. The .NET browser-rendering path stores each diagram
 * gzip-compressed (not byte-stable across runtimes, plan §6.4), so structural tests assert against the
 * decoded source rather than the inline bytes.
 */
public final class PumlData {

    private static final String OPEN = "<script id=\"puml-data\" type=\"application/json\">";
    private static final Pattern PAIR =
        Pattern.compile("\"([^\"]+)\":\"((?:\\\\u[0-9A-Fa-f]{4}|\\\\.|[^\"\\\\])*)\"");

    private PumlData() {
    }

    /** Diagram id → raw PlantUML, in document order (empty if there is no puml-data island). */
    public static Map<String, String> map(String html) {
        Map<String, String> out = new LinkedHashMap<>();
        int start = html.indexOf(OPEN);
        if (start < 0) {
            return out;
        }
        int contentStart = start + OPEN.length();
        int end = html.indexOf("</script>", contentStart);
        String json = html.substring(contentStart, end);
        Matcher m = PAIR.matcher(json);
        while (m.find()) {
            out.put(m.group(1), gunzip(Base64.getDecoder().decode(jsonUnescape(m.group(2)))));
        }
        return out;
    }

    /** All decoded PlantUML joined with newlines — convenient for {@code contains} assertions. */
    public static String all(String html) {
        return String.join("\n", map(html).values());
    }

    private static String jsonUnescape(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char n = s.charAt(++i);
            switch (n) {
                case 'u' -> {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                    i += 4;
                }
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                default -> out.append(n);
            }
        }
        return out.toString();
    }

    private static String gunzip(byte[] gz) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
