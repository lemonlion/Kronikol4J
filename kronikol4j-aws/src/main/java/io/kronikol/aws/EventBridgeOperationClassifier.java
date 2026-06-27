package io.kronikol.aws;

import io.kronikol.core.tracking.TrackingVerbosity;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classifies Amazon EventBridge requests into typed operations from the {@code X-Amz-Target} header
 * ({@code AWSEvents.<Operation>}), extracting the event-bus / rule names and {@code PutEvents} entry metadata
 * (count + first entry's {@code DetailType}/{@code Source}) from the request body. Java port of the .NET
 * {@code EventBridgeOperationClassifier}. Pure logic — no AWS SDK dependency.
 */
public final class EventBridgeOperationClassifier {

    private static final Map<String, EventBridgeOperation> TARGET_MAPPING = buildTargetMapping();

    private static final Pattern EVENT_BUS_NAME =
        Pattern.compile("\"EventBusName\"\\s*:\\s*\"(?<v>[^\"]+)\"");
    private static final Pattern NAME = Pattern.compile("\"Name\"\\s*:\\s*\"(?<v>[^\"]+)\"");
    private static final Pattern DETAIL_TYPE = Pattern.compile("\"DetailType\"\\s*:\\s*\"(?<v>[^\"]+)\"");
    private static final Pattern SOURCE = Pattern.compile("\"Source\"\\s*:\\s*\"(?<v>[^\"]+)\"");

    private EventBridgeOperationClassifier() {
    }

    /** Classifies an EventBridge request from the {@code X-Amz-Target} header value and the request body. */
    public static EventBridgeOperationInfo classify(String xAmzTarget, String requestBody) {
        EventBridgeOperation operation = xAmzTarget == null
            ? EventBridgeOperation.OTHER
            : TARGET_MAPPING.getOrDefault(xAmzTarget.trim(), EventBridgeOperation.OTHER);

        if (requestBody != null && operation == EventBridgeOperation.PUT_EVENTS) {
            Integer entryCount = countEntries(requestBody);
            return new EventBridgeOperationInfo(operation, firstMatch(EVENT_BUS_NAME, requestBody), null,
                firstMatch(DETAIL_TYPE, requestBody), firstMatch(SOURCE, requestBody), entryCount);
        }
        if (requestBody != null && (operation == EventBridgeOperation.PUT_RULE
            || operation == EventBridgeOperation.DELETE_RULE
            || operation == EventBridgeOperation.DESCRIBE_RULE)) {
            return new EventBridgeOperationInfo(operation, firstMatch(EVENT_BUS_NAME, requestBody),
                firstMatch(NAME, requestBody), null, null, null);
        }
        return new EventBridgeOperationInfo(operation);
    }

    /** The diagram label for an operation at the given verbosity (matches the .NET {@code GetDiagramLabel}). */
    public static String getDiagramLabel(EventBridgeOperationInfo op, TrackingVerbosity verbosity) {
        return switch (verbosity) {
            case RAW -> op.operation().displayName()
                + (op.eventBusName() != null ? " bus=" + op.eventBusName() : "")
                + (op.detailType() != null ? " type=" + op.detailType() : "")
                + (op.entryCount() != null ? " count=" + op.entryCount() : "");
            case DETAILED -> switch (op.operation()) {
                case PUT_EVENTS -> op.detailType() != null
                    ? "PutEvents [" + op.detailType() + "]" + (gt1(op.entryCount()) ? " x" + op.entryCount() : "")
                    : "PutEvents" + (op.entryCount() != null ? " x" + op.entryCount() : "");
                case PUT_RULE -> ("PutRule " + nullToEmpty(op.ruleName())).stripTrailing();
                case DELETE_RULE -> ("DeleteRule " + nullToEmpty(op.ruleName())).stripTrailing();
                case PUT_TARGETS -> "PutTargets";
                case REMOVE_TARGETS -> "RemoveTargets";
                case CREATE_EVENT_BUS -> ("CreateEventBus " + nullToEmpty(op.eventBusName())).stripTrailing();
                case DELETE_EVENT_BUS -> ("DeleteEventBus " + nullToEmpty(op.eventBusName())).stripTrailing();
                default -> op.operation().displayName();
            };
            case SUMMARISED -> switch (op.operation()) {
                case PUT_EVENTS -> "PutEvents";
                case PUT_RULE, DELETE_RULE -> "ManageRule";
                case PUT_TARGETS, REMOVE_TARGETS -> "ManageTargets";
                case CREATE_EVENT_BUS, DELETE_EVENT_BUS -> "ManageBus";
                default -> op.operation().displayName();
            };
        };
    }

    private static boolean gt1(Integer count) {
        return count != null && count > 1;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String firstMatch(Pattern p, String body) {
        Matcher m = p.matcher(body);
        return m.find() ? m.group("v") : null;
    }

    /** Counts the top-level objects in the {@code "Entries"} array (best-effort, string-aware brace scan). */
    private static Integer countEntries(String body) {
        int key = indexOfKey(body, "Entries");
        if (key < 0) {
            return null;
        }
        int start = body.indexOf('[', key);
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        char prev = 0;
        int count = 0;
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
                case '[', '{' -> {
                    if (c == '{' && depth == 1) {
                        count++; // an entry object directly inside the Entries array
                    }
                    depth++;
                }
                case ']', '}' -> {
                    depth--;
                    if (depth == 0) {
                        return count; // end of the Entries array
                    }
                }
                default -> { }
            }
            prev = c;
        }
        return count;
    }

    private static int indexOfKey(String body, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:").matcher(body);
        return m.find() ? m.start() : -1;
    }

    /** The {@code AWSEvents.<Op>} → operation map, case-insensitive (the .NET map is OrdinalIgnoreCase). */
    private static Map<String, EventBridgeOperation> buildTargetMapping() {
        Map<String, EventBridgeOperation> m = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (EventBridgeOperation op : EventBridgeOperation.values()) {
            if (op != EventBridgeOperation.OTHER) {
                m.put("AWSEvents." + op.displayName(), op);
            }
        }
        return m;
    }
}
