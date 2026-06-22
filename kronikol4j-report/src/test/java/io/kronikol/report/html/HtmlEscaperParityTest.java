package io.kronikol.report.html;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Cross-runtime parity for {@link HtmlEscaper#encode(String)} against the real .NET
 * {@code System.Net.WebUtility.HtmlEncode}. The {@code html-escape-samples.txt} fixture (captured by
 * {@code parity-harness/dotnet-capture}) pairs each input with WebUtility's exact output; every char that
 * is not printable ASCII is written {@code \\uXXXX} so the fixture stays clean ASCII. This pins the cases
 * the report goldens never feed — the apostrophe, the C1 controls (raw), the BMP above {@code U+00FF}
 * (raw), the Latin-1 supplement (escaped), and an astral emoji (combined code point).
 */
class HtmlEscaperParityTest {

    @Test
    void matchesWebUtilityHtmlEncode() throws IOException {
        String fixture = readFixture("parity/html-escape-samples.txt").replace("\r\n", "\n").replace("\r", "\n");
        int asserted = 0;
        for (String line : fixture.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            String input = unescape(line.substring(0, tab));
            String expected = unescape(line.substring(tab + 1));
            assertThat(HtmlEscaper.encode(input))
                .as("encode(%s)", line.substring(0, tab))
                .isEqualTo(expected);
            asserted++;
        }
        assertThat(asserted).isEqualTo(12); // every captured sample ran (guards an empty/truncated fixture)
    }

    /** Reverses the harness {@code FixtureEscape}: every {@code \\uXXXX} → its char (the only escape). */
    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 5 < s.length() && s.charAt(i + 1) == 'u') {
                sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 5;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String readFixture(String resource) throws IOException {
        try (InputStream in = HtmlEscaperParityTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("fixture not found: " + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
