package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.Method;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.model.PlantUmlForTest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns tracked {@link RequestResponseLog}s into PlantUML sequence-diagram text — the pure,
 * parity-critical heart of the output pipeline (plan §1 Seam C / Phase 2).
 *
 * <p>Parity discipline (plan §6.5): emits {@code \n} only; one un-split diagram per test
 * (client-side splitting — no Deflate-length decisions); deterministic participant order
 * (first-seen); ordinal sorts in note formatting. Diagram-type taxonomy: sequence (here);
 * component lives in {@code ComponentDiagramGenerator}; activity/flame arrive with the OTel bridge.
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
        byTest.forEach((testId, testLogs) -> {
            String testName = testLogs.get(0).testName();
            String diagram = buildDiagram(testLogs);
            result.add(new PlantUmlForTest(testId, testName, List.of(diagram), testLogs));
        });
        return result;
    }

    private static String buildDiagram(List<RequestResponseLog> logs) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("@startuml").append(NL);
        sb.append("skinparam wrapWidth 800").append(NL);
        sb.append("autonumber").append(NL);

        appendParticipants(sb, logs);
        sb.append(NL);

        for (RequestResponseLog log : logs) {
            // Override path: assertion notes and custom fragments render verbatim (plan §3.9).
            if (log.plantUml() != null) {
                sb.append(log.plantUml()).append(NL);
                continue;
            }
            String caller = alias(log.callerName());
            String service = alias(log.serviceName());
            if (log.type() == RequestResponseType.REQUEST) {
                sb.append(caller).append(" -> ").append(service)
                    .append(" : ").append(requestLabel(log)).append(NL);
            } else {
                sb.append(service).append(" --> ").append(caller)
                    .append(" : ").append(responseLabel(log)).append(NL);
            }
            appendNote(sb, NoteFormatter.format(log.content(), log.headers()));
        }

        sb.append("@enduml");
        return sb.toString();
    }

    private static void appendParticipants(StringBuilder sb, List<RequestResponseLog> logs) {
        // name -> category; callers default to null, services contribute their dependency category.
        Map<String, String> categories = new LinkedHashMap<>();
        for (RequestResponseLog log : logs) {
            if (log.plantUml() != null) {
                continue; // override fragments (e.g. assertion notes) declare no participants
            }
            categories.putIfAbsent(log.callerName(), null);
            String existing = categories.get(log.serviceName());
            if (!categories.containsKey(log.serviceName()) || existing == null) {
                categories.put(log.serviceName(), log.dependencyCategory());
            }
        }
        categories.forEach((name, category) -> {
            DependencyPalette.Shape shape = DependencyPalette.shapeFor(category);
            sb.append(shape.keyword()).append(" \"").append(name).append("\" as ").append(alias(name));
            String color = DependencyPalette.colorFor(category);
            if (color != null) {
                sb.append(' ').append(color);
            }
            sb.append(NL);
        });
    }

    private static void appendNote(StringBuilder sb, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        sb.append("note right").append(NL).append(body).append(NL).append("end note").append(NL);
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
        return log.method().value() + " " + path;
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

    /** Sanitizes a participant name into a PlantUML alias. Mirrors .NET {@code SanitizeAlias}
     *  (ASCII range {@code [^a-zA-Z0-9_]} -> {@code _}; no Unicode {@code \w} ambiguity, §6.5). */
    static String alias(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isEmpty() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        return sanitized;
    }

    // Exposed for callers that want a verb string without constructing a Method.
    static String methodValue(Method method) {
        return method.value();
    }
}
