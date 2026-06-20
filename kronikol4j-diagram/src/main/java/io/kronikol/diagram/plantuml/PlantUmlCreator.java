package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.RequestResponseLog;
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
 * <p>The output is aligned <strong>byte-for-byte</strong> with the .NET {@code PlantUmlCreator} for
 * the covered corpus (verified by the golden-file parity tests): the {@code !pragma teoz}/
 * {@code autonumber 1} prefix, {@code actor}/{@code entity} participant keywords with camelCased
 * aliases, the {@code METHOD: path} arrow labels, and request-note-left / response-note-right.
 * Parity discipline (plan §6.5): {@code \n} only; one un-split diagram per test (client-side
 * splitting); deterministic first-seen participant order.
 */
public final class PlantUmlCreator {

    private static final String NL = "\n";

    private PlantUmlCreator() {
    }

    /** Groups logs by test (first-seen order) and builds one diagram per test. */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs) {
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
                List.of(buildDiagram(testLogs)), testLogs)));
        return result;
    }

    private static String buildDiagram(List<RequestResponseLog> logs) {
        StringBuilder sb = new StringBuilder(512);
        // Prefix — matches the .NET CreatePlantUmlPrefix exactly (the two blank lines come from the
        // empty event/assertion styling sections).
        sb.append("@startuml").append(NL);
        sb.append("!pragma teoz true").append(NL);
        sb.append(NL).append(NL);
        sb.append("skinparam wrapWidth 800").append(NL);
        sb.append("autonumber 1").append(NL);
        sb.append(NL);

        appendParticipants(sb, logs);
        sb.append(NL);

        for (RequestResponseLog log : logs) {
            if (log.plantUml() != null) { // override path (assertion notes, custom fragments) — §3.9
                sb.append(log.plantUml()).append(NL);
                continue;
            }
            String caller = alias(log.callerName());
            String service = alias(log.serviceName());
            if (log.type() == RequestResponseType.REQUEST) {
                sb.append(caller).append(" -> ").append(service).append(": ")
                    .append(requestLabel(log)).append(NL);
                appendNote(sb, "left", NoteFormatter.format(log.content(), log.headers()));
            } else {
                sb.append(service).append(" --> ").append(caller).append(": ")
                    .append(responseLabel(log)).append(NL);
                appendNote(sb, "right", NoteFormatter.format(log.content(), log.headers()));
            }
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private static void appendParticipants(StringBuilder sb, List<RequestResponseLog> logs) {
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
            String keyword = services.contains(name)
                ? DependencyPalette.shapeFor(categoryByService.get(name)).keyword()
                : "actor";
            sb.append(keyword).append(" \"").append(name).append("\" as ").append(alias(name)).append(NL);
        }
    }

    private static void appendNote(StringBuilder sb, String side, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        sb.append("note ").append(side).append(NL).append(body).append(NL).append("end note").append(NL);
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
        return log.method().value() + ": " + path; // ".NET emits 'POST: /path'"
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

    /** Camelizes then sanitizes a participant name into a PlantUML alias (mirrors the .NET
     *  {@code SanitizeAlias} = {@code Camelize()} then {@code [^a-zA-Z0-9_] -> _}). E.g.
     *  {@code "OrderService" -> "orderService"}, {@code "Test" -> "test"}. */
    static String alias(String name) {
        String camel = camelize(name);
        String sanitized = camel.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    /** Lower-cases the first character (sufficient for Pascal/single-token names; full Humanizer
     *  parity for multi-word names is a future refinement, plan §6.5). */
    private static String camelize(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }
}
