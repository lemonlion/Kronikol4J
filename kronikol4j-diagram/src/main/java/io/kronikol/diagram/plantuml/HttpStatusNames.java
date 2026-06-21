package io.kronikol.diagram.plantuml;

import java.util.Map;

/**
 * Renders an HTTP status code the way .NET does in diagrams: {@code HttpStatusCode.ToString()}
 * (the PascalCase enum name) then {@code Titleize()} (words split on case). E.g. {@code 404 ->
 * "Not Found"}, {@code 200 -> "OK"}, {@code 500 -> "Internal Server Error"}. Unknown codes render
 * as the bare number (plan §6.5). Casing is invariant — no locale sensitivity.
 */
public final class HttpStatusNames {

    private HttpStatusNames() {
    }

    // The subset of System.Net.HttpStatusCode names that appear in practice. Extend as parity needs.
    private static final Map<Integer, String> NAMES = Map.ofEntries(
        Map.entry(200, "OK"),
        Map.entry(201, "Created"),
        Map.entry(202, "Accepted"),
        Map.entry(204, "NoContent"),
        Map.entry(301, "MovedPermanently"),
        Map.entry(302, "Found"),
        Map.entry(304, "NotModified"),
        Map.entry(307, "TemporaryRedirect"),
        Map.entry(308, "PermanentRedirect"),
        Map.entry(400, "BadRequest"),
        Map.entry(401, "Unauthorized"),
        Map.entry(403, "Forbidden"),
        Map.entry(404, "NotFound"),
        Map.entry(405, "MethodNotAllowed"),
        Map.entry(409, "Conflict"),
        Map.entry(410, "Gone"),
        Map.entry(422, "UnprocessableEntity"),
        Map.entry(429, "TooManyRequests"),
        Map.entry(500, "InternalServerError"),
        Map.entry(502, "BadGateway"),
        Map.entry(503, "ServiceUnavailable"),
        Map.entry(504, "GatewayTimeout"));

    /** The raw .NET {@code HttpStatusCode} enum name for a code (e.g. {@code 200 -> "OK"},
     *  {@code 404 -> "NotFound"}); unknown codes return the bare number. Used where .NET emits
     *  {@code HttpStatusCode.ToString()} verbatim (report-data {@code statusCode}). */
    public static String enumName(int code) {
        String name = NAMES.get(code);
        return name == null ? Integer.toString(code) : name;
    }

    /** The display label for an HTTP code, e.g. {@code "Not Found"}. */
    public static String label(int code) {
        String name = NAMES.get(code);
        String base = name == null ? Integer.toString(code) : titleize(name);
        // .NET disambiguates 302 ("Found" is unclear to a reader) with an explicit suffix.
        return code == 302 ? base + " (Redirect)" : base;
    }

    /**
     * Splits a PascalCase identifier into space-separated words, preserving runs of capitals
     * (so {@code "OK"} stays {@code "OK"}, {@code "NotFound" -> "Not Found"}). Invariant casing.
     */
    static String titleize(String pascal) {
        StringBuilder sb = new StringBuilder(pascal.length() + 4);
        for (int i = 0; i < pascal.length(); i++) {
            char c = pascal.charAt(i);
            boolean boundary = i > 0
                && Character.isUpperCase(c)
                && Character.isLowerCase(pascal.charAt(i - 1));
            if (boundary) {
                sb.append(' ');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
