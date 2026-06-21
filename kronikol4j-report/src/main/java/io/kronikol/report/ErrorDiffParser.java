package io.kronikol.report;

import io.kronikol.report.html.HtmlEscaper;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Byte-for-byte port of the .NET {@code ErrorDiffParser}. Extracts expected/actual value pairs from
 * assertion failure messages (xUnit / NUnit / FluentAssertions / Shouldly formats) and renders the
 * character-level LCS diff shown beneath the failure cause in the report.
 */
final class ErrorDiffParser {

    record DiffResult(String expected, String actual) {
    }

    // Patterns ordered by specificity (mirroring the .NET array + RegexOptions).
    private static final Pattern[] EXPECTED_ACTUAL_PATTERNS = {
        Pattern.compile("Expected:\\s*\"?(.+?)\"?\\s*\\r?\\nActual:\\s*\"?(.+?)\"?\\s*$", Pattern.MULTILINE),
        Pattern.compile("Expected:\\s*\"?(.+?)\"?\\s*\\r?\\n\\s*But was:\\s*\"?(.+?)\"?\\s*$", Pattern.MULTILINE),
        Pattern.compile("Expected string to be(?:\\s+equivalent to)?\\s+\"(.+?)\".+?but\\s+\"(.+?)\"", Pattern.DOTALL),
        Pattern.compile("should be\\s+\"(.+?)\"\\s*\\r?\\n\\s*but was\\s+\"(.+?)\"", Pattern.DOTALL),
    };

    private ErrorDiffParser() {
    }

    static DiffResult tryParseExpectedActual(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return null;
        }
        for (Pattern pattern : EXPECTED_ACTUAL_PATTERNS) {
            Matcher match = pattern.matcher(errorMessage);
            if (match.find()) {
                String expected = trimQuotes(match.group(1).strip());
                String actual = trimQuotes(match.group(2).strip());
                return new DiffResult(expected, actual);
            }
        }
        return null;
    }

    static String generateDiffHtml(String expected, String actual) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"error-diff\">");
        sb.append("<div class=\"diff-expected\"><span class=\"diff-label\">Expected:</span><code>");
        appendDiffChars(sb, expected, actual, true);
        sb.append("</code></div>");
        sb.append("<div class=\"diff-actual\"><span class=\"diff-label\">Actual:</span><code>");
        appendDiffChars(sb, actual, expected, false);
        sb.append("</code></div>");
        sb.append("</div>");
        return sb.toString();
    }

    private static void appendDiffChars(StringBuilder sb, String primary, String other, boolean isDeletion) {
        String lcs = computeLcs(primary, other);
        String cssClass = isDeletion ? "diff-del" : "diff-ins";
        int pi = 0;
        int li = 0;
        boolean inDiff = false;
        while (pi < primary.length()) {
            if (li < lcs.length() && primary.charAt(pi) == lcs.charAt(li)) {
                if (inDiff) {
                    sb.append("</span>");
                    inDiff = false;
                }
                sb.append(HtmlEscaper.encode(String.valueOf(primary.charAt(pi))));
                pi++;
                li++;
            } else {
                if (!inDiff) {
                    sb.append("<span class=\"").append(cssClass).append("\">");
                    inDiff = true;
                }
                sb.append(HtmlEscaper.encode(String.valueOf(primary.charAt(pi))));
                pi++;
            }
        }
        if (inDiff) {
            sb.append("</span>");
        }
    }

    private static String computeLcs(String a, String b) {
        int m = a.length();
        int n = b.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                    ? dp[i - 1][j - 1] + 1
                    : Math.max(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        StringBuilder sb = new StringBuilder();
        int x = m;
        int y = n;
        while (x > 0 && y > 0) {
            if (a.charAt(x - 1) == b.charAt(y - 1)) {
                sb.insert(0, a.charAt(x - 1));
                x--;
                y--;
            } else if (dp[x - 1][y] >= dp[x][y - 1]) {
                x--;
            } else {
                y--;
            }
        }
        return sb.toString();
    }

    /** Mirrors .NET {@code string.Trim('"')} — strips leading/trailing double-quotes. */
    private static String trimQuotes(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '"') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '"') {
            end--;
        }
        return s.substring(start, end);
    }
}
