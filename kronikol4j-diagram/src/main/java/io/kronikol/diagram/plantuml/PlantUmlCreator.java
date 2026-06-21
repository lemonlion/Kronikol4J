package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseMetaType;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.model.PlantUmlForTest;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns tracked {@link RequestResponseLog}s into PlantUML sequence-diagram text — the pure,
 * parity-critical heart of the output pipeline (plan §1 Seam C / Phase 2).
 *
 * <p>Output is aligned <strong>byte-for-byte</strong> with the .NET {@code PlantUmlCreator} (verified
 * by the golden-file parity tests): the {@code !pragma teoz}/{@code autonumber 1} prefix with the
 * conditional {@code .eventNote} {@code <style>} block; {@code actor}/{@code entity}/{@code database}/
 * {@code queue}/{@code collections} participant keywords with camelCased aliases; {@code METHOD: path}
 * arrows (optionally colour-coded per dependency type, e.g. {@code -[#438DD5]>}); request-note-left /
 * response-note-right; and {@code note<<eventNote>>} for fire-and-forget events. Parity discipline
 * (plan §6.5): {@code \n} only; one un-split diagram per test; deterministic first-seen order.
 */
public final class PlantUmlCreator {

    private static final String NL = "\n";

    // Exact .NET prefix fragments (the event/assertion style sections are empty -> a single blank line).
    private static final String EVENT_STYLE =
        "<style>\n .eventNote {\n     BackgroundColor #cfecf7\n     FontSize 11\n     RoundCorner 10\n }\n</style>\n";

    private PlantUmlCreator() {
    }

    /** Groups logs by test and builds one diagram per test (arrows uncoloured). */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs) {
        return create(logs, false);
    }

    /** As {@link #create(List)}, with optional per-dependency-type arrow colouring (.NET default: on). */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs, boolean arrowColors) {
        return create(logs, arrowColors, false);
    }

    /**
     * As {@link #create(List, boolean)}, additionally appending each <em>categorised</em> participant's
     * dependency colour to its declaration — the .NET {@code sequenceDiagramParticipantColors} mode
     * (e.g. {@code entity "OrderService" as orderService #438DD5}). The un-categorised caller actor and
     * any null-category service get <em>no</em> colour suffix, matching .NET exactly.
     */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs, boolean arrowColors,
                                               boolean participantColors) {
        Map<String, List<RequestResponseLog>> byTest = new LinkedHashMap<>();
        for (RequestResponseLog log : logs) {
            if (log.trackingIgnore()) {
                continue;
            }
            byTest.computeIfAbsent(log.testId(), k -> new ArrayList<>()).add(log);
        }

        List<PlantUmlForTest> result = new ArrayList<>();
        byTest.forEach((testId, testLogs) ->
            result.add(new PlantUmlForTest(testId, testLogs.get(0).testName(),
                List.of(buildDiagram(testLogs, arrowColors, participantColors)), testLogs)));
        return result;
    }

    private static String buildDiagram(List<RequestResponseLog> logs, boolean arrowColors,
                                       boolean participantColors) {
        boolean hasEvents = logs.stream()
            .anyMatch(l -> l.plantUml() == null && l.metaType() == RequestResponseMetaType.EVENT);

        StringBuilder sb = new StringBuilder(512);
        sb.append("@startuml").append(NL);
        sb.append("!pragma teoz true").append(NL);
        sb.append(hasEvents ? EVENT_STYLE : NL); // event style section (or empty -> blank line)
        sb.append(NL);                            // assertion style section (empty -> blank line)
        sb.append("skinparam wrapWidth 800").append(NL);
        sb.append("autonumber 1").append(NL);
        sb.append(NL);

        appendParticipants(sb, logs, participantColors);
        sb.append(NL);

        for (RequestResponseLog log : logs) {
            if (log.plantUml() != null) { // override path (assertion notes, custom fragments) — §3.9
                sb.append(log.plantUml()).append(NL);
                continue;
            }
            String caller = alias(log.callerName());
            String service = alias(log.serviceName());
            String color = DependencyPalette.colorFor(log.dependencyCategory());
            String side;
            if (log.type() == RequestResponseType.REQUEST) {
                String arrow = arrowColors ? "-[" + color + "]>" : "->";
                sb.append(caller).append(' ').append(arrow).append(' ').append(service)
                    .append(": ").append(requestLabel(log)).append(NL);
                side = "left";
            } else {
                String arrow = arrowColors ? "-[" + color + "]->" : "-->";
                sb.append(service).append(' ').append(arrow).append(' ').append(caller)
                    .append(": ").append(responseLabel(log)).append(NL);
                side = "right";
            }
            String opener = (log.metaType() == RequestResponseMetaType.EVENT ? "note<<eventNote>> " : "note ") + side;
            appendNote(sb, opener, NoteFormatter.format(log.content(), log.headers()));
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private static void appendParticipants(StringBuilder sb, List<RequestResponseLog> logs,
                                           boolean participantColors) {
        Set<String> order = new LinkedHashSet<>();          // first-seen participant order
        Set<String> services = new HashSet<>();             // names that appear as a service
        Map<String, String> categoryByService = new HashMap<>(); // first non-null category per service
        for (RequestResponseLog log : logs) {
            if (log.plantUml() != null) {
                continue; // override fragments declare no participants
            }
            order.add(log.callerName());
            order.add(log.serviceName());
            services.add(log.serviceName());
            if (log.dependencyCategory() != null) {
                categoryByService.putIfAbsent(log.serviceName(), log.dependencyCategory());
            }
        }
        for (String name : order) {
            // A service is shaped by its (first non-null) category; a caller-only name is an actor.
            String category = services.contains(name) ? categoryByService.get(name) : null;
            String keyword = services.contains(name) ? DependencyPalette.shapeFor(category) : "actor";
            sb.append(keyword).append(" \"").append(name).append("\" as ").append(alias(name));
            // Participant-colour mode colours only categorised participants (never the actor / a
            // null-category service), matching .NET's `participantColors && category is not null`.
            if (participantColors && category != null) {
                sb.append(' ').append(DependencyPalette.colorFor(category));
            }
            sb.append(NL);
        }
    }

    private static void appendNote(StringBuilder sb, String opener, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        sb.append(opener).append(NL).append(body).append(NL).append("end note").append(NL);
    }

    private static String requestLabel(RequestResponseLog log) {
        URI uri = log.uri();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }
        return log.method().value() + ": " + path;
    }

    private static String responseLabel(RequestResponseLog log) {
        StatusCode status = log.statusCode();
        if (status instanceof StatusCode.Http http) {
            return HttpStatusNames.label(http.code());
        }
        if (status instanceof StatusCode.Custom custom) {
            return custom.value();
        }
        return "";
    }

    /** Camelizes then sanitizes a participant name into a PlantUML alias (mirrors .NET {@code
     *  SanitizeAlias}). E.g. {@code "OrderService" -> "orderService"}, {@code "Test" -> "test"}. */
    static String alias(String name) {
        String sanitized = camelize(name).replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    private static String camelize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
