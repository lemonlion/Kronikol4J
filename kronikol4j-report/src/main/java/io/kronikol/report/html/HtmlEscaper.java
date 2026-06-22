package io.kronikol.report.html;

/**
 * HTML-escapes text to match .NET's {@code System.Net.WebUtility.HtmlEncode} (plan §4.4/§6.4) — the
 * encoder the .NET report uses at its 60+ call-sites. Java's {@code commons-text}/Spring encoders differ
 * (named vs numeric entities, quote handling), so we reproduce WebUtility's exact algorithm, pinned against
 * real WebUtility output by {@code HtmlEscaperParityTest} ({@code html-escape-samples.txt}):
 *
 * <ul>
 *   <li>{@code <}/{@code >}/{@code &}/{@code "} → {@code &lt;}/{@code &gt;}/{@code &amp;}/{@code &quot;},
 *       and {@code '} → {@code &#39;} (WebUtility <em>does</em> escape the apostrophe);</li>
 *   <li>only the Latin-1 supplement {@code U+00A0..U+00FF} is escaped to a numeric entity {@code &#NNN;};</li>
 *   <li>a surrogate pair is escaped to its <em>combined</em> code point ({@code 😀} → {@code &#128512;});</li>
 *   <li>everything else is left raw — including the C1 controls {@code U+0080..U+009F}, the rest of the BMP
 *       {@code U+0100..U+FFFF} ({@code ✓}, {@code €}, …), the ASCII controls {@code <0x20} (tab/newline),
 *       and lone surrogates.</li>
 * </ul>
 */
public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    public static String encode(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = null; // allocate lazily — most strings need no escaping
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            String replacement;
            int extraConsumed = 0;
            if (c == '<') {
                replacement = "&lt;";
            } else if (c == '>') {
                replacement = "&gt;";
            } else if (c == '&') {
                replacement = "&amp;";
            } else if (c == '"') {
                replacement = "&quot;";
            } else if (c == '\'') {
                replacement = "&#39;"; // WebUtility escapes the apostrophe (numeric)
            } else if (c >= 0xA0 && c <= 0xFF) {
                replacement = "&#" + (int) c + ";"; // Latin-1 supplement only
            } else if (Character.isHighSurrogate(c) && i + 1 < n
                && Character.isLowSurrogate(text.charAt(i + 1))) {
                // a valid surrogate pair → the combined Unicode scalar value
                replacement = "&#" + Character.toCodePoint(c, text.charAt(i + 1)) + ";";
                extraConsumed = 1;
            } else {
                replacement = null; // raw: 0x80-0x9F, 0x100-0xFFFF, controls < 0x20, lone surrogates
            }

            if (replacement == null) {
                if (sb != null) {
                    sb.append(c);
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder(n + 16);
                    sb.append(text, 0, i);
                }
                sb.append(replacement);
                i += extraConsumed; // skip the low surrogate we already consumed
            }
        }
        return sb == null ? text : sb.toString();
    }
}
