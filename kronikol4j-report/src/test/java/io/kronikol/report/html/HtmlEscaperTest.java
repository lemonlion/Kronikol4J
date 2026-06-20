package io.kronikol.report.html;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HtmlEscaperTest {

    @Test
    void escapesTheFourMarkupCharacters() {
        assertThat(HtmlEscaper.encode("<a href=\"x\"> & </a>"))
            .isEqualTo("&lt;a href=&quot;x&quot;&gt; &amp; &lt;/a&gt;");
    }

    @Test
    void escapesNonAsciiAsNumericEntities() {
        // WebUtility-style numeric entities (é = U+00E9 = 233, 你 = U+4F60 = 20320).
        assertThat(HtmlEscaper.encode("café 你好")).isEqualTo("caf&#233; &#20320;&#22909;");
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
