package io.kronikol.mongodb;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies MongoDB Atlas Data API REST requests into typed operations from the
 * {@code /action/{actionName}} endpoint path, extracting the data-source / database / collection / filter from
 * the request body. Java port of the .NET {@code AtlasDataApiOperationClassifier} — the HTTP-handler analog's
 * classification core. Pure logic — no Atlas/HTTP SDK dependency.
 */
public final class AtlasDataApiOperationClassifier {

    private static final Pattern ACTION_PATH =
        Pattern.compile("/action/(?<action>[a-zA-Z]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATA_SOURCE = stringField("dataSource");
    private static final Pattern DATABASE = stringField("database");
    private static final Pattern COLLECTION = stringField("collection");

    private AtlasDataApiOperationClassifier() {
    }

    /** Classifies an Atlas Data API request from its endpoint URI and (optional) JSON request body. */
    public static AtlasDataApiOperationInfo classify(URI uri, String bodyJson) {
        String path = uri == null || uri.getPath() == null ? "" : uri.getPath();
        Matcher m = ACTION_PATH.matcher(path);
        if (!m.find()) {
            return new AtlasDataApiOperationInfo(AtlasDataApiOperation.OTHER);
        }
        AtlasDataApiOperation operation = mapAction(m.group("action").toLowerCase(Locale.ROOT));

        if (bodyJson == null) {
            return new AtlasDataApiOperationInfo(operation, null, null, null, null);
        }
        return new AtlasDataApiOperationInfo(operation,
            firstMatch(DATA_SOURCE, bodyJson), firstMatch(DATABASE, bodyJson),
            firstMatch(COLLECTION, bodyJson), extractFilter(bodyJson));
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(AtlasDataApiOperationInfo op, TrackingVerbosity verbosity) {
        if (verbosity == TrackingVerbosity.DETAILED && op.collectionName() != null) {
            return op.operation().displayName() + " " + directionalArrow(op.operation()) + " " + op.collectionName();
        }
        // Detailed-without-collection, Summarised and Raw all fall back to the operation name (Raw's arrow
        // uses the HTTP method).
        return op.operation().displayName();
    }

    /** The directional arrow for an operation (reads ←, writes →, updates ↔). */
    static String directionalArrow(AtlasDataApiOperation operation) {
        return switch (operation) {
            case FIND_ONE, FIND, AGGREGATE -> "←";
            case INSERT_ONE, INSERT_MANY, DELETE_ONE, DELETE_MANY -> "→";
            case UPDATE_ONE, UPDATE_MANY, REPLACE_ONE -> "↔";
            default -> "→";
        };
    }

    private static AtlasDataApiOperation mapAction(String action) {
        return switch (action) {
            case "findone" -> AtlasDataApiOperation.FIND_ONE;
            case "find" -> AtlasDataApiOperation.FIND;
            case "insertone" -> AtlasDataApiOperation.INSERT_ONE;
            case "insertmany" -> AtlasDataApiOperation.INSERT_MANY;
            case "updateone" -> AtlasDataApiOperation.UPDATE_ONE;
            case "updatemany" -> AtlasDataApiOperation.UPDATE_MANY;
            case "deleteone" -> AtlasDataApiOperation.DELETE_ONE;
            case "deletemany" -> AtlasDataApiOperation.DELETE_MANY;
            case "replaceone" -> AtlasDataApiOperation.REPLACE_ONE;
            case "aggregate" -> AtlasDataApiOperation.AGGREGATE;
            default -> AtlasDataApiOperation.OTHER;
        };
    }

    private static Pattern stringField(String key) {
        return Pattern.compile("\"" + key + "\"\\s*:\\s*\"(?<v>[^\"]*)\"");
    }

    private static String firstMatch(Pattern p, String body) {
        Matcher m = p.matcher(body);
        return m.find() ? m.group("v") : null;
    }

    /** Extracts the {@code "filter"} value's text — a balanced object/array, or a quoted string. */
    private static String extractFilter(String body) {
        Matcher m = Pattern.compile("\"filter\"\\s*:\\s*").matcher(body);
        if (!m.find()) {
            return null;
        }
        int i = m.end();
        if (i >= body.length()) {
            return null;
        }
        char c = body.charAt(i);
        if (c == '{' || c == '[') {
            return balancedSpan(body, i);
        }
        if (c == '"') {
            Matcher s = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"").matcher(body);
            return s.find(i) && s.start() == i ? s.group() : null;
        }
        return null;
    }

    /** Returns the balanced {@code {...}}/{@code [...]} span starting at {@code start} (string-aware). */
    private static String balancedSpan(String body, int start) {
        int depth = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (c == '"' && prev != '\\') {
                    inString = false;
                }
                prev = c;
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{', '[' -> depth++;
                case '}', ']' -> {
                    depth--;
                    if (depth == 0) {
                        return body.substring(start, i + 1);
                    }
                }
                default -> { }
            }
            prev = c;
        }
        return body.substring(start); // unterminated — best effort
    }
}
