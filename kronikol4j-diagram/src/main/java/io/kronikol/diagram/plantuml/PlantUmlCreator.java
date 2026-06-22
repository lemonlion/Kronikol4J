package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.Header;
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

    /** Groups logs by test and builds one diagram per test, colouring arrows per the .NET default. */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs) {
        return create(logs, true);
    }

    /** As {@link #create(List)}, choosing per-dependency-type arrow colouring (participants uncoloured). */
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
        return create(logs, DiagramOptions.colours(arrowColors, participantColors));
    }

    /**
     * As {@link #create(List, boolean, boolean)}, prepending a {@code !theme <name>} directive (the .NET
     * {@code plantUmlTheme} option) right after {@code @startuml}. {@code null}/blank emits no theme.
     */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs, boolean arrowColors,
                                               boolean participantColors, String plantUmlTheme) {
        return create(logs,
            DiagramOptions.colours(arrowColors, participantColors).withPlantUmlTheme(plantUmlTheme));
    }

    /** Builds one diagram per test honouring the full {@link DiagramOptions} — the .NET
     *  {@code PlantUmlCreator.Create} surface (colours, theme, excluded headers, setup, focus). */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs, DiagramOptions options) {
        return create(logs, options, NoteProcessors.NONE);
    }

    /** As {@link #create(List, DiagramOptions)}, additionally applying the caller-supplied note content
     *  processors (the .NET pre/mid/post {@code Func<string,string>} formatting hooks). */
    public static List<PlantUmlForTest> create(List<RequestResponseLog> logs, DiagramOptions options,
                                               NoteProcessors processors) {
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
                List.of(buildDiagram(testLogs, options, processors)), testLogs)));
        return result;
    }

    private static String buildDiagram(List<RequestResponseLog> logs, DiagramOptions options,
                                       NoteProcessors processors) {
        boolean arrowColors = options.arrowColors();
        boolean hasEvents = logs.stream()
            .anyMatch(l -> l.plantUml() == null && l.metaType() == RequestResponseMetaType.EVENT);

        // Setup/assertion separation (.NET separateSetup): wrap the setup-phase traces in a
        // "partition <color> Setup" … "end" block, closed when the IsActionStart marker is reached.
        int actionStartIndex = -1;
        for (int i = 0; i < logs.size(); i++) {
            if (logs.get(i).actionStart()) {
                actionStartIndex = i;
                break;
            }
        }
        boolean hasActionStart = options.separateSetup() && actionStartIndex >= 0;
        boolean hasSetupTraces = false;
        for (int i = 0; hasActionStart && i < actionStartIndex; i++) {
            if (logs.get(i).plantUml() == null && !logs.get(i).actionStart()) {
                hasSetupTraces = true;
                break;
            }
        }
        String setupColor = options.setupHighlightColor() != null ? options.setupHighlightColor() : "#F6F6F6";
        String partitionLine = options.highlightSetup() ? "partition " + setupColor + " Setup" : "partition Setup";
        boolean partitionOpen = false;
        boolean setupPartitionClosed = false;

        StringBuilder sb = new StringBuilder(512);
        sb.append("@startuml").append(NL);
        if (options.plantUmlTheme() != null && !options.plantUmlTheme().isBlank()) {
            sb.append("!theme ").append(options.plantUmlTheme()).append(NL); // .NET themeDirective, before !pragma
        }
        sb.append("!pragma teoz true").append(NL);
        sb.append(hasEvents ? EVENT_STYLE : NL); // event style section (or empty -> blank line)
        sb.append(NL);                            // assertion style section (empty -> blank line)
        sb.append("skinparam wrapWidth 800").append(NL);
        sb.append("autonumber 1").append(NL);
        sb.append(NL);

        appendParticipants(sb, logs, options);
        sb.append(NL);

        // .NET _serviceCategoryCache: serviceName/callerName → category (override-aware, first non-null
        // across ALL traces) — drives the arrow colours, including the caller-category fallback.
        Map<String, String> serviceCategoryCache = buildServiceCategoryCache(logs, options.serviceTypeOverrides());

        for (RequestResponseLog log : logs) {
            if (log.actionStart()) { // phase-boundary marker — closes Setup, never rendered (.NET parity)
                if (partitionOpen) {
                    sb.append("end").append(NL);
                    partitionOpen = false;
                }
                setupPartitionClosed = true;
                continue;
            }
            if (log.plantUml() != null) { // override path (assertion notes, custom fragments) — §3.9
                if (hasActionStart && !setupPartitionClosed) { // .NET closes the Setup partition before an override
                    if (partitionOpen) {
                        sb.append("end").append(NL);
                        partitionOpen = false;
                    }
                    setupPartitionClosed = true;
                }
                sb.append(log.plantUml()).append(NL);
                continue;
            }
            if (hasSetupTraces && !partitionOpen && !setupPartitionClosed) { // open Setup before the first setup trace
                sb.append(partitionLine).append(NL);
                partitionOpen = true;
            }
            String caller = alias(log.callerName());
            String service = alias(log.serviceName());
            String arrowColor = arrowColorSyntax(serviceCategoryCache, log, arrowColors, options.dependencyColors());
            boolean isRequest = log.type() == RequestResponseType.REQUEST;
            // .NET pre/mid/post Func hooks: pre on raw content (also feeds the GraphQL request label),
            // mid inside the formatter (before focus), post on the final note body.
            String content = NoteProcessors.apply(
                isRequest ? processors.requestPre() : processors.responsePre(), log.content());
            String side;
            if (isRequest) {
                String arrow = "-" + arrowColor + ">"; // .NET: "-{arrowColor}>" (arrowColor incl. brackets)
                sb.append(caller).append(' ').append(arrow).append(' ').append(service)
                    .append(": ").append(requestLabel(log, content, options.internalFlowTracking())).append(NL);
                side = log.noteOnRight() ? "right" : "left"; // .NET trace.NoteOnRight (request note side)
            } else {
                String arrow = "-" + arrowColor + "->"; // .NET: "-{arrowColor}->"
                sb.append(service).append(' ').append(arrow).append(' ').append(caller)
                    .append(": ").append(responseLabel(log)).append(NL);
                side = "right";
            }
            String opener = (log.metaType() == RequestResponseMetaType.EVENT ? "note<<eventNote>> " : "note ") + side;
            // .NET: excludeAllHeaders passes [] to FormatNoteContent (drops every header from the note).
            List<Header> noteHeaders = options.excludeAllHeaders() ? List.of() : log.headers();
            String note = NoteFormatter.format(content, noteHeaders, options.excludedHeaders(),
                log.focusFields(), options.focusEmphasis(), options.focusDeEmphasis(),
                isRequest ? processors.requestMid() : processors.responseMid(), isRequest,
                options.graphQlBodyFormat());
            note = NoteProcessors.apply(isRequest ? processors.requestPost() : processors.responsePost(), note);
            note = truncateNote(note, options.truncateNotesAfterLines()); // .NET TruncateNoteContent
            appendNote(sb, opener, note);
        }

        sb.append("@enduml");
        return sb.toString();
    }

    /** Declares the participants — a faithful port of .NET {@code CreateEntitiesPlantUml}: the pure caller
     *  (a {@code CallerName} that never appears as a {@code ServiceName}) is declared first; callers and
     *  services are shaped by their (override-aware, first-non-null) category — a category-less caller
     *  becomes {@code entity} once an actor exists, else {@code actor}; participant colours honour the
     *  {@code dependencyColors} override map. */
    private static void appendParticipants(StringBuilder sb, List<RequestResponseLog> logs,
                                           DiagramOptions options) {
        boolean participantColors = options.participantColors();
        Map<String, String> overrides = options.serviceTypeOverrides();
        Map<String, String> dependencyColors = options.dependencyColors();

        List<RequestResponseLog> relevant = new ArrayList<>();
        for (RequestResponseLog log : logs) {
            if (log.overrideStart() || log.overrideEnd() || log.actionStart() || log.plantUml() != null) {
                continue; // override fragments + action-start markers declare no participants
            }
            relevant.add(log);
        }

        Set<String> allServiceNames = new HashSet<>(); // case-sensitive (.NET default-comparer HashSet)
        for (RequestResponseLog t : relevant) {
            allServiceNames.add(t.serviceName());
        }
        String pureCaller = null;
        for (RequestResponseLog t : relevant) {
            if (!allServiceNames.contains(t.callerName())) {
                pureCaller = t.callerName();
                break;
            }
        }

        // callerName → category (override, else first non-null CallerDependencyCategory; entry always made).
        Map<String, String> callerCategories = caseInsensitiveMap();
        for (RequestResponseLog t : relevant) {
            if (callerCategories.containsKey(t.callerName())) {
                continue;
            }
            callerCategories.put(t.callerName(), overrides.containsKey(t.callerName())
                ? overrides.get(t.callerName()) : firstNonNullCategory(relevant, t.callerName(), true));
        }

        Set<String> currentPlayers = new HashSet<>(); // aliases already declared
        boolean actorDefined = false;
        if (pureCaller != null) {
            String pureCallerAlias = alias(pureCaller);
            currentPlayers.add(pureCallerAlias);
            String pureCallerCategory = callerCategories.get(pureCaller);
            if (pureCallerCategory != null) {
                sb.append(DependencyPalette.shapeFor(pureCallerCategory)).append(" \"").append(pureCaller)
                    .append("\" as ").append(pureCallerAlias);
                if (participantColors) {
                    sb.append(' ').append(DependencyPalette.colorFor(pureCallerCategory, dependencyColors));
                }
                sb.append(NL);
            } else {
                sb.append("actor \"").append(pureCaller).append("\" as ").append(pureCallerAlias).append(NL);
            }
            actorDefined = true;
        }

        // serviceName → category (override, else first non-null DependencyCategory; entry always made).
        Map<String, String> serviceCategories = caseInsensitiveMap();
        for (RequestResponseLog t : relevant) {
            if (serviceCategories.containsKey(t.serviceName())) {
                continue;
            }
            serviceCategories.put(t.serviceName(), overrides.containsKey(t.serviceName())
                ? overrides.get(t.serviceName()) : firstNonNullCategory(relevant, t.serviceName(), false));
        }

        for (RequestResponseLog t : relevant) {
            String callerShortName = alias(t.callerName());
            if (currentPlayers.add(callerShortName)) {
                String callerCategory = callerCategories.get(t.callerName());
                if (callerCategory != null) {
                    sb.append(DependencyPalette.shapeFor(callerCategory)).append(" \"").append(t.callerName())
                        .append("\" as ").append(callerShortName);
                    if (participantColors) {
                        sb.append(' ').append(DependencyPalette.colorFor(callerCategory, dependencyColors));
                    }
                    sb.append(NL);
                } else { // category-less caller: entity once an actor exists, else actor (.NET actorDefined)
                    sb.append(actorDefined ? "entity" : "actor").append(" \"").append(t.callerName())
                        .append("\" as ").append(callerShortName).append(NL);
                }
            }

            String serviceShortName = alias(t.serviceName());
            if (currentPlayers.add(serviceShortName)) {
                String category = serviceCategories.get(t.serviceName());
                sb.append(DependencyPalette.shapeFor(category)).append(" \"").append(t.serviceName())
                    .append("\" as ").append(serviceShortName);
                if (participantColors && category != null) {
                    sb.append(' ').append(DependencyPalette.colorFor(category, dependencyColors));
                }
                sb.append(NL);
            }
        }
    }

    /** .NET {@code BuildServiceCategoryCache}: {@code serviceName}/{@code callerName} → category over
     *  <em>all</em> traces. A service is always cached (its first non-null {@code DependencyCategory}, or
     *  the override, possibly {@code null}); a caller is cached only when it has an override or a non-null
     *  {@code CallerDependencyCategory}. Drives the arrow colours. */
    private static Map<String, String> buildServiceCategoryCache(List<RequestResponseLog> logs,
                                                                 Map<String, String> overrides) {
        Map<String, String> cache = caseInsensitiveMap();
        for (RequestResponseLog t : logs) {
            if (!cache.containsKey(t.serviceName())) {
                cache.put(t.serviceName(), overrides.containsKey(t.serviceName())
                    ? overrides.get(t.serviceName()) : firstNonNullCategory(logs, t.serviceName(), false));
            }
            if (!cache.containsKey(t.callerName())) {
                if (overrides.containsKey(t.callerName())) {
                    cache.put(t.callerName(), overrides.get(t.callerName()));
                } else {
                    String callerCat = firstNonNullCategory(logs, t.callerName(), true);
                    if (callerCat != null) {
                        cache.put(t.callerName(), callerCat);
                    }
                }
            }
        }
        return cache;
    }

    /** The arrow colour syntax (e.g. {@code [#E74C3C]}) for a trace, or {@code ""} when colouring is off —
     *  .NET {@code GetArrowColor}: the service's cached category (else its own), falling back to the
     *  caller's when the service has none, then {@code GetColor(category, dependencyColors)}. */
    private static String arrowColorSyntax(Map<String, String> cache, RequestResponseLog log,
                                           boolean arrowColors, Map<String, String> dependencyColors) {
        if (!arrowColors) {
            return "";
        }
        String category = cache.containsKey(log.serviceName())
            ? cache.get(log.serviceName()) : log.dependencyCategory();
        if ((category == null || category.isEmpty()) && log.callerName() != null) {
            category = cache.containsKey(log.callerName())
                ? cache.get(log.callerName()) : log.callerDependencyCategory();
        }
        return "[" + DependencyPalette.colorFor(category, dependencyColors) + "]";
    }

    /** The first non-null {@code DependencyCategory} ({@code isCaller=false}) or {@code CallerDependencyCategory}
     *  ({@code isCaller=true}) among traces whose service/caller name equals {@code name} (case-sensitive,
     *  matching .NET {@code ==}); {@code null} if none. */
    private static String firstNonNullCategory(List<RequestResponseLog> traces, String name, boolean isCaller) {
        for (RequestResponseLog t : traces) {
            if (isCaller && name.equals(t.callerName()) && t.callerDependencyCategory() != null) {
                return t.callerDependencyCategory();
            }
            if (!isCaller && name.equals(t.serviceName()) && t.dependencyCategory() != null) {
                return t.dependencyCategory();
            }
        }
        return null;
    }

    /** A category lookup map matching the .NET caches' {@code StringComparer.OrdinalIgnoreCase} (null
     *  values allowed; keys are non-null participant names). */
    private static Map<String, String> caseInsensitiveMap() {
        return new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    /** .NET {@code TruncateNoteContent}: cap the note at {@code maxLines} lines, the rest replaced by a
     *  trailing {@code ...} line. {@code maxLines <= 0} disables truncation. Applied after note formatting
     *  + escaping — equivalent to .NET's truncate-before-escape because backslash-doubling preserves
     *  newlines (so {@code truncate∘escape == escape∘truncate}). */
    static String truncateNote(String noteContent, int maxLines) {
        if (maxLines <= 0) {
            return noteContent;
        }
        String[] lines = noteContent.split("\n", -1); // -1 keeps trailing empties (.NET String.Split('\n'))
        if (lines.length <= maxLines) {
            return noteContent;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        sb.append("\n...");
        return sb.toString();
    }

    private static void appendNote(StringBuilder sb, String opener, String body) {
        if (body == null || body.isEmpty()) {
            return;
        }
        sb.append(opener).append(NL).append(body).append(NL).append("end note").append(NL);
    }

    private static final int MAX_URL_LENGTH = 100; // .NET maxUrlLength — wrap longer path+query

    private static String requestLabel(RequestResponseLog log, String content, boolean internalFlowTracking) {
        URI uri = log.uri();
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            path = path + "?" + query;
        }
        if (path.length() > MAX_URL_LENGTH) { // .NET: split a long URL into "\n        "-joined chunks
            StringBuilder wrapped = new StringBuilder();
            for (int i = 0; i < path.length(); i += MAX_URL_LENGTH) {
                if (i > 0) {
                    wrapped.append("\\n        "); // literal "\n" + 8 spaces — a PlantUML label line break
                }
                wrapped.append(path, i, Math.min(i + MAX_URL_LENGTH, path.length()));
            }
            path = wrapped.toString();
        }
        String label = log.method().value() + ": " + path;
        // .NET: a GraphQL request appends "\n(query GetUser)" / "(mutation …)" to the arrow label.
        String graphQlLabel = GraphQlOperationDetector.tryExtractLabel(content);
        if (graphQlLabel != null) {
            label = label + "\\n(" + graphQlLabel + ")";
        }
        // .NET: wrap the whole label in a "[[#iflow-<id> …]]" link the internal-flow popup keys on
        // (#iflow-<requestResponseId> matches the segment key from InternalFlowSegmentBuilder).
        if (internalFlowTracking) {
            label = "[[#iflow-" + log.requestResponseId() + " " + label + "]]";
        }
        return label;
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
    public static String alias(String name) {
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
