package io.kronikol.report.html;

/**
 * HTML-escapes text to match .NET's {@code System.Net.WebUtility.HtmlEncode} (plan §4.4/§6.4) — the
 * encoder the .NET report uses at 63 call-sites. Java's {@code commons-text}/Spring encoders differ
 * (named vs numeric entities, quote handling), so we reproduce WebUtility exactly: escape
 * {@code & < > "} and every non-ASCII char (&gt; 127) as a numeric entity.
 *
 * <p>NOTE: the precise treatment of {@code '} and the 128–159 range must be pinned against real
 * WebUtility output during golden-file parity (plan §6.3); the common cases below are faithful.
 */
public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    public static String encode(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = null; // allocate lazily — most strings need no escaping
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String replacement = switch (c) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                default -> c > 127 ? "&#" + (int) c + ";" : null;
            };
            if (replacement == null) {
                if (sb != null) {
                    sb.append(c);
                }
            } else {
                if (sb == null) {
                    sb = new StringBuilder(text.length() + 16);
                    sb.append(text, 0, i);
                }
                sb.append(replacement);
            }
        }
        return sb == null ? text : sb.toString();
    }
}
