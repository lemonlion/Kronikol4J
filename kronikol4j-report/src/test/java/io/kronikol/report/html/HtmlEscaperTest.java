package io.kronikol.report.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Focused unit checks for {@link HtmlEscaper}; the exhaustive cross-runtime pinning against real
 * {@code WebUtility.HtmlEncode} lives in {@link HtmlEscaperParityTest}.
 */
class HtmlEscaperTest {

    @Test
    void escapesTheMarkupCharactersAndApostrophe() {
        // WebUtility escapes < > & " and the apostrophe (as the numeric entity &#39;).
        assertThat(HtmlEscaper.encode("<a href=\"x\"> & </a> Bob's"))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt; &amp; &lt;/a&gt; Bob&#39;s");
    }

    @Test
    void escapesOnlyTheLatin1Supplement() {
        // WebUtility escapes only U+00A0..U+00FF to numeric entities (e-acute = U+00E9 = 233);
        // higher BMP (U+4F60) and the C1 controls (U+0085) stay raw.
        assertThat(HtmlEscaper.encode("caf\u00E9")).isEqualTo("caf&#233;");
        assertThat(HtmlEscaper.encode("\u4F60\u597D")).isEqualTo("\u4F60\u597D");
        assertThat(HtmlEscaper.encode("\u0085")).isEqualTo("\u0085");
    }

    @Test
    void combinesSurrogatePairsToOneCodePoint() {
        // U+1F600 -> the combined scalar &#128512; (not two surrogate halves).
        assertThat(HtmlEscaper.encode("\uD83D\uDE00")).isEqualTo("&#128512;");
    }

    @Test
    void leavesPlainAsciiUntouched() {
        String plain = "GET /orders/checkout 200 OK";
        assertThat(HtmlEscaper.encode(plain)).isSameAs(plain);
    }

    @Test
    void handlesNull() {
        assertThat(HtmlEscaper.encode(null)).isEmpty();
    }
}
