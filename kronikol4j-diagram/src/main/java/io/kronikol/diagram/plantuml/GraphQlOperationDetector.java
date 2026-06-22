package io.kronikol.diagram.plantuml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects GraphQL operations (query/mutation/subscription) in HTTP request bodies and extracts an
 * operation label (e.g. {@code "query GetUser"}, {@code "mutation CreateOrder"}, or just {@code "query"}
 * for anonymous) for the diagram arrow label — a faithful port of the .NET {@code GraphQlOperationDetector}.
 */
public final class GraphQlOperationDetector {

    // "query" : "<captured value>" — handles escaped chars inside the JSON string value.
    private static final Pattern QUERY_KEY_VALUE =
        Pattern.compile("\"query\"\\s*:\\s*\"((?:[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*))\"", Pattern.DOTALL);
    // leading JSON-escaped whitespace (\n \r \t) + literal whitespace
    private static final Pattern LEADING_JSON_WHITESPACE = Pattern.compile("^(?:\\\\[nrt]|\\s)+");
    // a GraphQL operation: query|mutation|subscription + optional name (stops at whitespace, '(' or '{')
    private static final Pattern OPERATION = Pattern.compile("^(query|mutation|subscription)(?:\\s+(\\w+))?");
    // an explicit "operationName" field in the JSON body
    private static final Pattern OPERATION_NAME = Pattern.compile("\"operationName\"\\s*:\\s*\"([^\"]*)\"");

    private GraphQlOperationDetector() {
    }

    public static String tryExtractLabel(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmedStart = content.stripLeading();
        if (trimmedStart.isEmpty() || trimmedStart.charAt(0) != '{') {
            return null;
        }

        Matcher queryMatch = QUERY_KEY_VALUE.matcher(content);
        if (!queryMatch.find() || !isAtTopLevel(content, queryMatch.start())) {
            return null;
        }
        String queryValue = queryMatch.group(1);
        String trimmed = LEADING_JSON_WHITESPACE.matcher(queryValue).replaceFirst("");

        String operationType;
        String inlineName = null;
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '{') {
            operationType = "query"; // anonymous query shorthand: { user { name } }
        } else {
            Matcher opMatch = OPERATION.matcher(trimmed);
            if (!opMatch.find()) {
                return null;
            }
            operationType = opMatch.group(1);
            if (opMatch.group(2) != null && !opMatch.group(2).isEmpty()) {
                inlineName = opMatch.group(2);
            }
        }

        String explicitName = null;
        Matcher opNameMatch = OPERATION_NAME.matcher(content);
        if (opNameMatch.find() && isAtTopLevel(content, opNameMatch.start())) {
            String val = opNameMatch.group(1);
            if (!val.isEmpty()) {
                explicitName = val;
            }
        }

        String name = explicitName != null ? explicitName : inlineName;
        return name != null ? operationType + " " + name : operationType;
    }

    /** True when {@code position} is inside the outermost JSON object (depth 1), skipping string literals. */
    static boolean isAtTopLevel(String content, int position) {
        int depth = 0;
        for (int i = 0; i < position; i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (c == '"') {
                i++;
                while (i < position) {
                    if (content.charAt(i) == '\\') {
                        i++;
                    } else if (content.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
            }
        }
        return depth == 1;
    }
}
