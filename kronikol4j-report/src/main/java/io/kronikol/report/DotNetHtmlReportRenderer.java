package io.kronikol.report;

import io.kronikol.report.html.HtmlEscaper;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.InlineParameterValue;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import io.kronikol.report.model.StepParameter;
import io.kronikol.report.model.StepTextSegment;
import io.kronikol.report.model.TableRowType;
import io.kronikol.report.model.TabularParameterValue;
import io.kronikol.report.model.TreeParameterValue;
import io.kronikol.report.model.VerificationStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * Byte-for-byte port of the .NET {@code ReportGenerator.GenerateHtmlReport} browser-rendering path
 * (plan §4). The head is assembled from the shared embedded assets in
 * {@code io/kronikol/report/assets/} (the very files the .NET build embeds), interleaved with the
 * literal template text and dynamic interpolations exactly as the C# raw-string literal renders
 * them — including the pure-whitespace lines that empty interpolation holes produce. The body ports
 * the filtering box, toolbar, scenario timeline and per-feature/scenario {@code <details>} markup.
 *
 * <p>Diagram PlantUML is gzip+base64-compressed into the {@code puml-data} JSON island, mirroring
 * {@code InternalFlowHtmlGenerator.CompressToBase64}. Gzip output is not byte-stable across runtimes
 * (plan §6.4), so the golden-file parity test asserts that block by <em>decoded</em> equality; every
 * other byte is identical.
 */
public final class DotNetHtmlReportRenderer {

    private static final String NL = "\n";

    /** Matches the .NET {@code TrackingDefaults.PlantUmlJsCdnBase}. */
    static final String PLANTUML_CDN_BASE =
        "https://cdn.jsdelivr.net/gh/lemonlion/plantuml-js-plantuml_limit_size_98304@v1.2026.3beta6-patched";

    /** The .NET {@code Constants.DefaultFavicon.DataUri} (compile-time concatenation, verbatim). */
    private static final String FAVICON_DATA_URI =
        "data:image/svg+xml;base64,"
        + "PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxMjgg"
        + "MTI4IiB3aWR0aD0iMTI4IiBoZWlnaHQ9IjEyOCI+DQogIDxkZWZzPg0KICAgIDxsaW5lYXJHcmFk"
        + "aWVudCBpZD0ic2Nyb2xsIiB4MT0iMCUiIHkxPSIwJSIgeDI9IjAlIiB5Mj0iMTAwJSI+DQogICAg"
        + "ICA8c3RvcCBvZmZzZXQ9IjAlIiBzdHlsZT0ic3RvcC1jb2xvcjojRjVFOENFO3N0b3Atb3BhY2l0"
        + "eToxIiAvPg0KICAgICAgPHN0b3Agb2Zmc2V0PSIxMDAlIiBzdHlsZT0ic3RvcC1jb2xvcjojRTBD"
        + "RkE4O3N0b3Atb3BhY2l0eToxIiAvPg0KICAgIDwvbGluZWFyR3JhZGllbnQ+DQogICAgPGxpbmVh"
        + "ckdyYWRpZW50IGlkPSJyb2xsVG9wIiB4MT0iMCUiIHkxPSIwJSIgeDI9IjAlIiB5Mj0iMTAwJSI+"
        + "DQogICAgICA8c3RvcCBvZmZzZXQ9IjAlIiBzdHlsZT0ic3RvcC1jb2xvcjojQzhBRDgwO3N0b3At"
        + "b3BhY2l0eToxIiAvPg0KICAgICAgPHN0b3Agb2Zmc2V0PSIzNSUiIHN0eWxlPSJzdG9wLWNvbG9y"
        + "OiNFRUUwQzQ7c3RvcC1vcGFjaXR5OjEiIC8+DQogICAgICA8c3RvcCBvZmZzZXQ9IjEwMCUiIHN0"
        + "eWxlPSJzdG9wLWNvbG9yOiNCODlFNkM7c3RvcC1vcGFjaXR5OjEiIC8+DQogICAgPC9saW5lYXJH"
        + "cmFkaWVudD4NCiAgICA8bGluZWFyR3JhZGllbnQgaWQ9InJvbGxCb3QiIHgxPSIwJSIgeTE9IjAl"
        + "IiB4Mj0iMCUiIHkyPSIxMDAlIj4NCiAgICAgIDxzdG9wIG9mZnNldD0iMCUiIHN0eWxlPSJzdG9w"
        + "LWNvbG9yOiNCODlFNkM7c3RvcC1vcGFjaXR5OjEiIC8+DQogICAgICA8c3RvcCBvZmZzZXQ9IjY1"
        + "JSIgc3R5bGU9InN0b3AtY29sb3I6I0VFRTBDNDtzdG9wLW9wYWNpdHk6MSIgLz4NCiAgICAgIDxz"
        + "dG9wIG9mZnNldD0iMTAwJSIgc3R5bGU9InN0b3AtY29sb3I6I0M4QUQ4MDtzdG9wLW9wYWNpdHk6"
        + "MSIgLz4NCiAgICA8L2xpbmVhckdyYWRpZW50Pg0KICA8L2RlZnM+DQoNCiAgPCEtLSBTY3JvbGwg"
        + "Ym9keSAtLT4NCiAgPHJlY3QgeD0iNCIgeT0iMTYiIHdpZHRoPSIxMjAiIGhlaWdodD0iOTYiIGZp"
        + "bGw9InVybCgjc2Nyb2xsKSIvPg0KDQogIDwhLS0gU2Nyb2xsIHRvcCByb2xsIC0tPg0KICA8cmVj"
        + "dCB4PSIwIiB5PSI2IiB3aWR0aD0iMTI4IiBoZWlnaHQ9IjIwIiByeD0iMTAiIGZpbGw9InVybCgj"
        + "cm9sbFRvcCkiLz4NCg0KICA8IS0tIFNjcm9sbCBib3R0b20gcm9sbCAtLT4NCiAgPHJlY3QgeD0i"
        + "MCIgeT0iMTAyIiB3aWR0aD0iMTI4IiBoZWlnaHQ9IjIwIiByeD0iMTAiIGZpbGw9InVybCgjcm9s"
        + "bEJvdCkiLz4NCg0KICA8IS0tIEFycm93IDE6IHJpZ2h0IC0tPg0KICA8bGluZSB4MT0iMjQiIHkx"
        + "PSI1MCIgeDI9Ijg0IiB5Mj0iNTAiIHN0cm9rZT0iIzEwMUUzQyIgc3Ryb2tlLXdpZHRoPSI5IiBz"
        + "dHJva2UtbGluZWNhcD0icm91bmQiLz4NCiAgPHBvbHlnb24gcG9pbnRzPSI4MSwzNyAxMDQsNTAg"
        + "ODEsNjMiIGZpbGw9IiMxMDFFM0MiLz4NCg0KICA8IS0tIEFycm93IDI6IGxlZnQgLS0+DQogIDxs"
        + "aW5lIHgxPSIxMDQiIHkxPSI4MCIgeDI9IjQ0IiB5Mj0iODAiIHN0cm9rZT0iIzEwMUUzQyIgc3Ry"
        + "b2tlLXdpZHRoPSI5IiBzdHJva2UtbGluZWNhcD0icm91bmQiLz4NCiAgPHBvbHlnb24gcG9pbnRz"
        + "PSI0Nyw5MyAyNCw4MCA0Nyw2NyIgZmlsbD0iIzEwMUUzQyIvPg0KPC9zdmc+DQo=";

    /** The inline {@code toggleTimelineFunction} up to (and including) the toggle call. .NET appends
     *  {@code deactivateComponentDiagramJs} here when a run-level component diagram is present, then
     *  the closing brace; otherwise just the closing brace. */
    private static final String TOGGLE_TIMELINE_HEAD =
        "function toggle_timeline(btn) {" + NL
        + "    var tl = document.getElementById('scenario-timeline');" + NL
        + "    if (!tl) return;" + NL
        + "    var hidden = tl.style.display === 'none';" + NL
        + "    tl.style.display = hidden ? '' : 'none';" + NL
        + "    btn.classList.toggle('timeline-toggle-active', hidden);";

    /** .NET status-filter button order: {@code ExecutionResult} sorted, minus SkippedAfterFailure. */
    private static final String[] STATUS_FILTER_ORDER = {"Passed", "Failed", "Skipped", "Bypassed"};

    private static final Pattern PARTICIPANT_RE = Pattern.compile(
        "^(?:actor|boundary|control|entity|database|collections|queue|participant)\\s+\"([^\"]+)\"");
    private static final Pattern URL_RE = Pattern.compile(
        ":\\s*(?:GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS|CONNECT|TRACE):\\s*(\\S+)");
    private static final Pattern DEPENDENCY_RE = Pattern.compile(
        "^(?:actor|boundary|control|entity|database|collections|queue|participant)\\s+\"([^\"]+)\"\\s+as\\s+");
    private static final Pattern ANCHOR_NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private static final Map<String, String> ASSET_CACHE = new ConcurrentHashMap<>();

    private DotNetHtmlReportRenderer() {
    }

    /**
     * Renders the full interactive HTML report, byte-identical to .NET's browser-rendering path.
     *
     * @param version emitted as {@code <meta name="generator" content="Kronikol v{version}">}
     */
    public static String render(List<Feature> features, Map<String, String> diagramByTestId,
                                String componentDiagram, String title, String version) {
        return render(features, diagramByTestId, componentDiagram, title, version,
            false, Instant.EPOCH, Instant.EPOCH);
    }

    /**
     * As {@link #render(List, Map, String, String, String)}, with the .NET {@code includeTestRunData}
     * block (the Features Summary table, the Test Execution Summary and the pie chart). {@code startTime}
     * / {@code endTime} feed the execution summary; they are ignored when {@code includeTestRunData} is
     * false (the production default).
     */
    public static String render(List<Feature> features, Map<String, String> diagramByTestId,
                                String componentDiagram, String title, String version,
                                boolean includeTestRunData, Instant startTime, Instant endTime) {
        boolean hasComponent = componentDiagram != null && !componentDiagram.isEmpty();
        Map<String, String> diagramData = new LinkedHashMap<>();
        StringBuilder head = new StringBuilder(300_000);
        appendHead(head, title, version, hasComponent);

        StringBuilder body = new StringBuilder(8_192);
        appendBody(body, title, version, features, diagramByTestId == null ? Map.of() : diagramByTestId,
            hasComponent ? componentDiagram : null, diagramData, includeTestRunData, startTime, endTime);

        StringBuilder html = new StringBuilder(head.length() + body.length() + 1_024);
        html.append(head).append(body);
        if (!diagramData.isEmpty()) {
            html.append("<script id=\"puml-data\" type=\"application/json\">")
                .append(pumlDataJson(diagramData))
                .append("</script>");
        }
        html.append("    </body>").append(NL).append("</html>");
        return html.toString();
    }

    // ----------------------------------------------------------------------------------- head -----

    private static void appendHead(StringBuilder sb, String title, String version,
                                   boolean hasComponentDiagram) {
        String plantUmlBrowserScript =
            asset("plantuml-browser-render-script.js").replace("__PLANTUML_CDN_BASE__", PLANTUML_CDN_BASE);
        String faviconLink = "<link rel=\"icon\" href=\"" + FAVICON_DATA_URI + "\">";
        String toggleTimelineFunction = hasComponentDiagram
            ? TOGGLE_TIMELINE_HEAD + asset("report-deactivate-component-diagram.js") + NL + "}"
            : TOGGLE_TIMELINE_HEAD + NL + "}";
        String toggleComponentDiagramFunction = hasComponentDiagram
            ? asset("report-toggle-component-diagram-function.js") : "";

        sb.append("<!DOCTYPE html>").append(NL);
        sb.append("<html>").append(NL);
        sb.append("    <head>").append(NL);
        sb.append("        <meta charset=\"utf-8\" />").append(NL);
        sb.append("        <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />").append(NL);
        sb.append("        <meta name=\"generator\" content=\"Kronikol v").append(version).append("\" />").append(NL);
        sb.append("        <title>").append(title).append("</title>").append(NL);
        sb.append("        <style>").append(NL);
        // combinedStylesheet = HtmlReportStyleSheet + "\n" + (stylesheet == null ? "" : ...) → trailing "\n"
        sb.append("            ").append(asset("stylesheets.css")).append(NL).append(NL);
        sb.append("            ").append(asset("context-menu-styles.css")).append(NL);
        sb.append("            ").append(asset("inline-svg-styles.css")).append(NL);
        sb.append("            ").append(asset("collapsible-notes-styles.css")).append(NL);
        sb.append("            ").append(NL);                              // internalFlowPopupStyles = ""
        sb.append("        </style>").append(NL);
        sb.append("        ").append(NL);                                  // customCssBlock = ""
        sb.append("        ").append(faviconLink).append(NL);
        sb.append("        <script>").append(NL);
        sb.append("            ").append(asset("advanced-search.js")).append(NL);
        sb.append("            ").append(asset("report-scenario-feature-map-helper.js")).append(NL);
        sb.append("            ").append(asset("report-toggle-happy-paths-function.js")).append(NL);
        sb.append("            ").append(asset("report-search-function.js")).append(NL);
        sb.append("            ").append(asset("report-dependency-filter-function.js")).append(NL);
        sb.append("            ").append(asset("report-category-filter-function.js")).append(NL);
        sb.append("            ").append(asset("report-status-filter-function.js")).append(NL);
        sb.append("            ").append(asset("report-collapse-expand-all-function.js")).append(NL);
        sb.append("            ").append(asset("report-lightbox-function.js")).append(NL);
        sb.append("            ").append(asset("report-toggle-table-ref-function.js")).append(NL);
        sb.append("            ").append(asset("report-sort-table-function.js")).append(NL);
        sb.append("            ").append(asset("report-copy-scenario-name-function.js")).append(NL);
        sb.append("            ").append(asset("report-toggle-examples-detail-function.js")).append(NL);
        sb.append("            ").append(asset("report-select-row-function.js")).append(NL);
        sb.append("            ").append(asset("report-toggle-flatten-params-js.js")).append(NL);
        sb.append("            ").append(asset("report-param-expand-js.js")).append(NL);
        sb.append("            ").append(toggleTimelineFunction).append(NL);
        sb.append("            ").append(toggleComponentDiagramFunction).append(NL);
        sb.append("            ").append(asset("report-jump-to-failure-function.js")).append(NL);
        sb.append("            ").append(asset("report-duration-filter-function.js")).append(NL);
        sb.append("            ").append(asset("report-export-function.js")).append(NL);
        sb.append("            ").append(asset("report-persistent-filter-function.js")).append(NL);
        sb.append("            ").append(asset("report-url-hash-function.js")).append(NL);
        sb.append("            ").append(asset("report-keyboard-navigation-function.js")).append(NL);
        sb.append("            ").append(asset("report-init-script.js")).append(NL);
        sb.append("            ").append(NL);                              // enrichSearchDataScript = ""
        sb.append("        </script>").append(NL);
        sb.append("        ").append(plantUmlBrowserScript).append(NL);
        sb.append("        ").append(asset("collapsible-notes-script.js")).append(NL);
        sb.append("        ").append(asset("context-menu-script.js")).append(NL);
        sb.append("        ").append(NL);                                  // flameChartRenderScript = ""
        sb.append("        ").append(NL);                                  // internalFlowDataScript = ""
        sb.append("        ").append(NL);                                  // internalFlowPopupScript = ""
        sb.append("        ").append(NL);                                  // toggleScript = ""
        sb.append("    </head>").append(NL);
        sb.append("    <body>");                                          // no trailing newline
    }

    // ----------------------------------------------------------------------------------- body -----

    private static void appendBody(StringBuilder body, String title, String version, List<Feature> features,
                                   Map<String, String> diagramByTestId, String componentDiagram,
                                   Map<String, String> diagramData, boolean includeTestRunData,
                                   Instant startTime, Instant endTime) {
        body.append("<h1>").append(title).append("</h1>");
        if (includeTestRunData) {
            appendTestRunDataSummary(body, features, version, startTime, endTime);
        }

        // Per-scenario dependencies + diagram search terms (drives data-* attributes + filters).
        Map<String, Set<String>> scenarioDependencies = new LinkedHashMap<>();
        Map<String, Set<String>> scenarioSearchTerms = new LinkedHashMap<>();
        Set<String> allDependencies = new TreeSet<>();
        boolean hasDurations = false;
        for (Feature feature : features) {
            for (Scenario scenario : feature.scenarios()) {
                if (scenario.durationMs() > 0) {
                    hasDurations = true;
                }
                Set<String> deps = new LinkedHashSet<>();
                Set<String> terms = new LinkedHashSet<>();
                String diagram = diagramByTestId.get(scenario.testId());
                if (diagram != null) {
                    deps.addAll(extractDependencies(diagram));
                    terms.addAll(extractDiagramSearchTerms(diagram));
                }
                scenarioDependencies.put(scenario.testId(), deps);
                scenarioSearchTerms.put(scenario.testId(), terms);
                allDependencies.addAll(deps);
            }
        }

        appendFilteringBox(body, features, allDependencies, hasDurations);
        if (includeTestRunData) {
            body.append("</div>"); // close header-row (opened in the test-execution-summary block)
        }
        appendToolbar(body, hasDurations, componentDiagram != null);
        appendTimeline(body, features, hasDurations);

        int counter = 0;
        if (componentDiagram != null) {
            String compId = "puml-" + (counter++);
            diagramData.put(compId, compressToBase64(componentDiagram));
            body.append("<div id=\"component-diagram\" class=\"component-diagram-section\" style=\"display:none\">")
                .append("<div class=\"plantuml-browser\" id=\"").append(compId)
                .append("\" data-diagram-type=\"plantuml\"></div></div>");
        }
        body.append("<div id=\"report-content\">");
        int paramGroupCounter = 0;
        for (Feature feature : features) {
            boolean featureHasFailures = feature.scenarios().stream()
                .anyMatch(s -> s.status() == ExecutionStatus.FAILED);
            boolean featureAllSkipped = !featureHasFailures && !feature.scenarios().isEmpty()
                && feature.scenarios().stream().allMatch(s -> s.status() == ExecutionStatus.SKIPPED);
            String summaryClass = featureHasFailures ? " failed" : featureAllSkipped ? " skipped" : "";
            String endpoint = feature.endpoint() == null ? ""
                : " <div class=\"endpoint\">" + feature.endpoint() + "</div>";
            String labels = feature.labels().isEmpty() ? ""
                : concatLabels(feature.labels(), null);
            body.append("<details class=\"feature\">").append(NL)
                .append("   <summary class=\"h2").append(summaryClass).append("\">")
                .append(feature.displayName()).append(endpoint).append(labels).append("</summary>");
            if (feature.description() != null) {
                body.append("<div class=\"feature-description\">")
                    .append(HtmlEscaper.encode(feature.description())).append("</div>");
            }

            List<Scenario> ordered = new ArrayList<>(feature.scenarios());
            ordered.sort(Comparator.comparing(Scenario::isHappyPath).reversed()
                .thenComparing(Scenario::name));

            // Parameterized grouping (by OutlineId) folded into the rule grouping, with .NET's
            // first-encounter rendering: a group renders once, at its first scenario.
            List<ParameterGrouper.ParameterizedGroup> paramGroups = ParameterGrouper.analyze(ordered, 10);
            Map<String, ParameterGrouper.ParameterizedGroup> scenarioToGroup = new HashMap<>();
            for (ParameterGrouper.ParameterizedGroup g : paramGroups) {
                for (Scenario s : g.scenarios()) {
                    scenarioToGroup.put(s.testId(), g);
                }
            }
            Set<String> renderedGroupKeys = new HashSet<>();
            String currentRule = "__NOTSET__";
            boolean ruleOpen = false;
            for (Scenario scenario : ordered) {
                ParameterGrouper.ParameterizedGroup group = scenarioToGroup.get(scenario.testId());
                String groupKey = null;
                if (group != null) {
                    groupKey = group.groupDisplayName() + "|" + group.scenarios().stream()
                        .map(Scenario::testId).collect(java.util.stream.Collectors.joining(","));
                    if (renderedGroupKeys.contains(groupKey)) {
                        continue;
                    }
                }
                if (!Objects.equals(scenario.rule(), currentRule)) {
                    if (ruleOpen) {
                        body.append("</details>"); // close previous rule
                    }
                    currentRule = scenario.rule();
                    if (currentRule != null) {
                        body.append("<details class=\"rule\" open><summary class=\"h2-5\">")
                            .append(HtmlEscaper.encode(currentRule)).append("</summary>");
                        ruleOpen = true;
                    } else {
                        ruleOpen = false;
                    }
                }
                if (group != null) {
                    renderedGroupKeys.add(groupKey);
                    appendParameterizedGroup(body, feature, group, "pgrp" + (paramGroupCounter++),
                        scenarioDependencies, scenarioSearchTerms);
                    continue;
                }
                counter = appendScenario(body, feature, scenario, diagramByTestId,
                    scenarioDependencies, scenarioSearchTerms, diagramData, counter);
            }
            if (ruleOpen) {
                body.append("</details>"); // close last rule
            }
            body.append("</details>");
        }
        body.append("</div>");

        long failureCount = features.stream().flatMap(f -> f.scenarios().stream())
            .filter(s -> s.status() == ExecutionStatus.FAILED).count();
        if (failureCount > 0) {
            body.append("<button class=\"jump-to-failure\" onclick=\"jump_to_next_failure()\">Next Failure "
                + "<span class=\"failure-counter\" id=\"failure-counter\">(0/").append(failureCount)
                .append(")</span></button>");
        }
        body.append("<button class=\"back-to-top\" id=\"back-to-top\" "
            + "onclick=\"window.scrollTo({top:0,behavior:'smooth'})\">↑</button>");
    }

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC);

    /** The .NET {@code includeTestRunData} block: the Features Summary table, the Test Execution
     *  Summary (which opens {@code <div class="header-row">}, closed after the filtering box) and the
     *  pie chart. */
    private static void appendTestRunDataSummary(StringBuilder body, List<Feature> features,
                                                 String version, Instant startTime, Instant endTime) {
        List<Scenario> scenarios = features.stream().flatMap(f -> f.scenarios().stream()).toList();
        long passed = scenarios.stream().filter(s -> s.status() == ExecutionStatus.PASSED).count();
        long skipped = scenarios.stream().filter(s -> s.status() == ExecutionStatus.SKIPPED).count();
        long failed = scenarios.stream().filter(s -> s.status() == ExecutionStatus.FAILED).count();
        long bypassed = scenarios.stream().filter(s -> s.status() == ExecutionStatus.INCONCLUSIVE).count();
        String overallStatus = failed > 0 ? "Failed" : "Passed";

        boolean hasAnySteps = features.stream()
            .anyMatch(f -> f.scenarios().stream().anyMatch(s -> !s.steps().isEmpty()));
        boolean hasAnyDurations = features.stream()
            .anyMatch(f -> f.scenarios().stream().anyMatch(s -> s.durationMs() > 0));

        body.append("<details class=\"features-summary-details\"><summary class=\"h2\">Features Summary</summary>");
        body.append("<div class=\"features-summary-table-wrapper\">");
        body.append("<table class=\"feature-summary-table\"><thead><tr>");
        body.append("<th onclick=\"sort_table(0)\">Feature</th>");
        body.append("<th onclick=\"sort_table(1)\">Scenarios</th>");
        body.append("<th onclick=\"sort_table(2)\">Passed</th>");
        body.append("<th onclick=\"sort_table(3)\">Failed</th>");
        body.append("<th onclick=\"sort_table(4)\">Skipped</th>");
        int nextCol = 5;
        if (hasAnySteps) {
            body.append("<th onclick=\"sort_table(").append(nextCol++).append(")\">Steps</th>");
            body.append("<th class=\"step-status-header\" onclick=\"sort_table(").append(nextCol++).append(")\">Passed</th>");
            body.append("<th class=\"step-status-header\" onclick=\"sort_table(").append(nextCol++).append(")\">Failed</th>");
            body.append("<th class=\"step-status-header\" onclick=\"sort_table(").append(nextCol++).append(")\">Skipped</th>");
        }
        if (hasAnyDurations) {
            body.append("<th onclick=\"sort_table(").append(nextCol++).append(")\">Duration</th>");
            body.append("<th onclick=\"sort_table(").append(nextCol++).append(")\">Avg</th>");
            body.append("<th onclick=\"sort_table(").append(nextCol).append(")\">Longest</th>");
        }
        body.append("</tr></thead><tbody>");

        for (Feature feature : features) {
            int totalSc = feature.scenarios().size();
            long passedSc = feature.scenarios().stream().filter(s -> s.status() == ExecutionStatus.PASSED).count();
            long failedSc = feature.scenarios().stream().filter(s -> s.status() == ExecutionStatus.FAILED).count();
            long skippedSc = feature.scenarios().stream()
                .filter(s -> s.status() == ExecutionStatus.SKIPPED || s.status() == ExecutionStatus.INCONCLUSIVE).count();
            body.append("<tr").append(failedSc > 0 ? " class=\"failed\"" : "").append(">");
            body.append("<td>").append(HtmlEscaper.encode(feature.displayName())).append("</td>");
            body.append("<td>").append(totalSc).append("</td>");
            body.append("<td>").append(passedSc).append("</td>");
            body.append("<td>").append(failedSc).append("</td>");
            body.append("<td>").append(skippedSc).append("</td>");
            if (hasAnySteps) {
                List<ScenarioStep> allSteps = feature.scenarios().stream()
                    .flatMap(s -> s.steps().stream()).toList();
                int[] byStatus = new int[3];
                int stepCount = countStepsRecursive(allSteps, byStatus);
                body.append("<td>").append(stepCount).append("</td>");
                body.append("<td>").append(byStatus[0]).append("</td>");
                body.append("<td>").append(byStatus[1]).append("</td>");
                body.append("<td>").append(byStatus[2]).append("</td>");
            }
            if (hasAnyDurations) {
                long[] durations = feature.scenarios().stream()
                    .filter(s -> s.durationMs() > 0).mapToLong(Scenario::durationMs).toArray();
                long totalDur = 0;
                long maxDur = 0;
                for (long d : durations) {
                    totalDur += d;
                    maxDur = Math.max(maxDur, d);
                }
                long avgDur = durations.length > 0 ? totalDur / durations.length : 0;
                body.append("<td>").append(formatDuration(totalDur)).append("</td>");
                body.append("<td>").append(formatDuration(avgDur)).append("</td>");
                body.append("<td>").append(formatDuration(maxDur)).append("</td>");
            }
            body.append("</tr>");
        }
        body.append("</tbody></table></div></details>");

        // Test Execution Summary (opens header-row; closed after the filtering box).
        body.append("<div class=\"header-row\">").append(NL)
            .append("<div class=\"test-execution-summary\">").append(NL)
            .append("    <h2>Test Execution Summary</h2>").append(NL)
            .append("    <table>").append(NL)
            .append("        <tr><td colspan=\"2\" class=\"column-header\">Execution</td><td colspan=\"2\" class=\"column-header\">Content</td></tr>").append(NL)
            .append("        <tr><td>Overall status:</td><td>").append(overallStatus).append("</td><td>Features: </td><td>").append(features.size()).append("</td></tr>").append(NL)
            .append("        <tr><td>Start Date:</td><td>").append(DATE_FMT.format(startTime)).append(" (UTC)</td><td>Scenarios: </td><td>").append(scenarios.size()).append("</td></tr>").append(NL)
            .append("        <tr><td>Start Time:</td><td>").append(TIME_FMT.format(startTime)).append(" (UTC)</td><td>Passed Scenarios: </td><td>").append(passed).append("</td></tr>").append(NL)
            .append("        <tr><td>End Time:</td><td>").append(TIME_FMT.format(endTime)).append(" (UTC)</td><td>Failed Scenarios: </td><td>").append(failed).append("</td></tr>").append(NL)
            .append("        <tr><td>Duration:</td><td>").append(formatDuration(Duration.between(startTime, endTime).toMillis())).append("</td><td>Skipped Scenarios: </td><td>").append(skipped).append("</td></tr>").append(NL)
            .append("        <tr style=\"display:none\"><td>Kronikol Version:</td><td>").append(version).append("</td><td></td><td></td></tr>").append(NL)
            .append("    </table>").append(NL)
            .append("</div>");

        body.append(generatePieChartSvg((int) passed, (int) failed, (int) skipped, (int) bypassed));
    }

    /** Total step count (recursive) + status tally into {@code byStatus} = [passed, failed, skipped]
     *  (skipped folds in bypassed) — mirrors .NET CountStepsRecursive + CountStepsByStatusRecursive. */
    private static int countStepsRecursive(List<ScenarioStep> steps, int[] byStatus) {
        int count = steps.size();
        for (ScenarioStep step : steps) {
            switch (step.status()) {
                case PASSED -> byStatus[0]++;
                case FAILED -> byStatus[1]++;
                default -> byStatus[2]++; // SKIPPED + INCONCLUSIVE(bypassed)
            }
            if (!step.subSteps().isEmpty()) {
                count += countStepsRecursive(step.subSteps(), byStatus);
            }
        }
        return count;
    }

    /** Mirrors .NET {@code FormatDuration} (component-based: ms / seconds / minutes+seconds). */
    private static String formatDuration(long durationMs) {
        long total = Math.abs(durationMs);
        if (total < 1000) {
            return total + "ms";
        }
        if (total < 60_000) {
            return (total / 1000) + "s";
        }
        return (total / 60_000) + "m " + ((total / 1000) % 60) + "s";
    }

    private static String generatePieChartSvg(int passed, int failed, int skipped, int bypassed) {
        int total = passed + failed + skipped + bypassed;
        if (total == 0) {
            return "";
        }
        int passRate = (int) Math.rint(100.0 * passed / total);
        record Seg(double pct, String color, String label, int count) {
        }
        List<Seg> segments = new ArrayList<>();
        if (passed > 0) {
            segments.add(new Seg(100.0 * passed / total, "#1daf26", "Passed", passed));
        }
        if (failed > 0) {
            segments.add(new Seg(100.0 * failed / total, "#cc0000", "Failed", failed));
        }
        if (skipped > 0) {
            segments.add(new Seg(100.0 * skipped / total, "#949494", "Skipped", skipped));
        }
        if (bypassed > 0) {
            segments.add(new Seg(100.0 * bypassed / total, "#2e7bff", "Bypassed", bypassed));
        }
        double radius = 40;
        double circumference = 2 * Math.PI * radius;
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"summary-chart\">");
        sb.append("<svg viewBox=\"0 0 100 100\">");
        double offset = 0.0;
        for (Seg seg : segments) {
            double dash = circumference * seg.pct() / 100.0;
            double gap = circumference - dash;
            double dashOffset = -offset * circumference / 100.0;
            sb.append("<circle cx=\"50\" cy=\"50\" r=\"").append(f1(radius))
                .append("\" fill=\"none\" stroke=\"").append(seg.color())
                .append("\" stroke-width=\"12\" stroke-dasharray=\"").append(f2(dash)).append(" ").append(f2(gap))
                .append("\" stroke-dashoffset=\"").append(f2(dashOffset))
                .append("\" transform=\"rotate(-90 50 50)\"><title>").append(seg.label()).append(": ")
                .append(seg.count()).append(" (").append(f0(seg.pct())).append("%)</title></circle>");
            offset += seg.pct();
        }
        sb.append("<text x=\"50\" y=\"50\" text-anchor=\"middle\" dominant-baseline=\"central\" "
            + "font-size=\"16\" font-weight=\"bold\" fill=\"#333\">").append(passRate).append("%</text>");
        sb.append("</svg></div>");
        return sb.toString();
    }

    private static void appendFilteringBox(StringBuilder body, List<Feature> features,
                                           Set<String> allDependencies, boolean hasDurations) {
        body.append("<div class=\"filtering-box\">").append(NL)
            .append("   <div class=\"filtering-box-header\"><h2>Filtering</h2><div class=\"filtering-box-export\"><button class=\"export-btn\" onclick=\"clear_all_filters()\">Clear All</button><button class=\"export-btn\" onclick=\"export_html()\">Export Filtered HTML</button><button class=\"export-btn\" onclick=\"export_csv()\">Export Filtered CSV</button></div></div>").append(NL)
            .append("   <div class=\"filter-search\"><input id=\"searchbar\" placeholder=\"Search... (@tag, $status, &&, ||, !!, parentheses)\" onkeyup=\"search_scenarios()\" /><button type=\"button\" class=\"search-help-toggle\" onclick=\"toggle_search_help()\" title=\"Search syntax help\">?</button></div>").append(NL)
            .append("   <div class=\"mobile-filter-toggle\">Filters</div>").append(NL)
            .append("   <div class=\"filters\">").append(NL)
            .append("   <div class=\"search-help-panel\" style=\"display:none\">").append(NL)
            .append("   <table class=\"search-help-table\">").append(NL)
            .append("   <tr><th>Syntax</th><th>Meaning</th><th>Example</th></tr>").append(NL)
            .append("   <tr><td><code>word</code></td><td>Text search (feature name, scenario name, step text, tags, diagram source)</td><td><code>order</code></td></tr>").append(NL)
            .append("   <tr><td><code>\"phrase\"</code></td><td>Exact phrase match</td><td><code>\"create order\"</code></td></tr>").append(NL)
            .append("   <tr><td><code>&&</code></td><td>AND — both sides must match</td><td><code>order && create</code></td></tr>").append(NL)
            .append("   <tr><td><code>||</code></td><td>OR — either side must match</td><td><code>payment || order</code></td></tr>").append(NL)
            .append("   <tr><td><code>!!</code></td><td>NOT — excludes matches</td><td><code>order && !!delete</code></td></tr>").append(NL)
            .append("   <tr><td><code>( )</code></td><td>Parentheses — group expressions</td><td><code>(a || b) && c</code></td></tr>").append(NL)
            .append("   <tr><td><code>@tag</code></td><td>Filter by tag / category</td><td><code>@smoke && @api</code></td></tr>").append(NL)
            .append("   <tr><td><code>$status</code></td><td>Filter by status</td><td><code>$failed</code>, <code>$passed</code>, <code>$skipped</code></td></tr>").append(NL)
            .append("   </table>").append(NL)
            .append("   <p class=\"search-help-note\">Space-separated words use implicit AND. Press <kbd>/</kbd> to focus the search bar. Operators <code>&&</code> <code>||</code> <code>!!</code> activate advanced mode; without them, legacy tag expressions (<code>@a and @b or not @c</code>) are also supported.</p>").append(NL)
            .append("   </div>").append(NL)
            .append("   <div class=\"filter-row\">");

        body.append("<div class=\"status-filters\"><span class=\"status-filters-label\">Status:</span>");
        for (String status : STATUS_FILTER_ORDER) {
            body.append("<button class=\"status-toggle\" data-status=\"").append(status)
                .append("\" onclick=\"toggle_status(this)\">").append(status).append("</button>");
        }
        body.append("</div>");
        body.append("   <div class=\"happy-path-filters\"><span class=\"happy-path-filters-label\">Happy Paths:</span><button class=\"happy-path-toggle\" onclick=\"toggle_happy_paths(this)\">Happy Paths Only</button></div>");
        body.append("</div>"); // close filter-row

        if (hasDurations) {
            double[] p = durationPercentiles(features);
            body.append("<div class=\"duration-filters\" data-p50=\"").append(f0(p[0]))
                .append("\" data-p90=\"").append(f0(p[1])).append("\" data-p95=\"").append(f0(p[2]))
                .append("\" data-p99=\"").append(f0(p[3]))
                .append("\"><span class=\"duration-filters-label\">Duration ≥:</span>")
                .append("<button class=\"percentile-btn\" data-threshold-ms=\"").append(f0(p[0])).append("\" onclick=\"set_percentile(this)\">P50 (").append(formatDurationBadge(p[0])).append(")</button>")
                .append("<button class=\"percentile-btn\" data-threshold-ms=\"").append(f0(p[1])).append("\" onclick=\"set_percentile(this)\">P90 (").append(formatDurationBadge(p[1])).append(")</button>")
                .append("<button class=\"percentile-btn\" data-threshold-ms=\"").append(f0(p[2])).append("\" onclick=\"set_percentile(this)\">P95 (").append(formatDurationBadge(p[2])).append(")</button>")
                .append("<button class=\"percentile-btn\" data-threshold-ms=\"").append(f0(p[3])).append("\" onclick=\"set_percentile(this)\">P99 (").append(formatDurationBadge(p[3])).append(")</button>")
                .append("<button class=\"percentile-btn\" data-custom=\"1\" onclick=\"set_percentile(this)\">Custom</button>")
                .append("<span id=\"custom-duration-wrap\" style=\"display:none;align-items:center;gap:0.3em\"><input id=\"duration-threshold\" type=\"number\" step=\"0.1\" min=\"0\" placeholder=\"seconds\" onchange=\"filter_duration()\" /><span class=\"duration-filters-unit\">seconds</span></span></div>");
        }

        if (!allDependencies.isEmpty()) {
            body.append("<div class=\"dependency-filters\"><span class=\"dependency-filters-label\">Dependencies:</span><button class=\"dep-mode-toggle\" title=\"AND: show scenarios matching ALL selected dependencies. OR: show scenarios matching ANY selected dependency. Click to toggle.\" onclick=\"toggle_dep_mode(this)\">AND</button>");
            for (String dep : allDependencies) {
                String enc = HtmlEscaper.encode(dep);
                body.append("<button class=\"dependency-toggle\" data-dependency=\"").append(enc)
                    .append("\" onclick=\"toggle_dependency(this)\">").append(enc).append("</button>");
            }
            body.append("</div>");
        }

        body.append("</div>"); // close filters
        body.append("</div>"); // close filtering-box
    }

    private static void appendToolbar(StringBuilder body, boolean hasDurations, boolean hasComponent) {
        body.append("<div class=\"toolbar-row\">");
        body.append("<div class=\"toolbar-left\"><button class=\"collapse-expand-all\" onclick=\"toggle_expand_collapse(this, 'details.feature', 'Expand All Features', 'Collapse All Features')\">Expand All Features</button><button class=\"collapse-expand-all\" onclick=\"toggle_expand_collapse(this, 'details.scenario', 'Expand All Scenarios', 'Collapse All Scenarios')\">Expand All Scenarios</button>");
        if (hasDurations) {
            body.append("<button class=\"timeline-toggle\" onclick=\"toggle_timeline(this)\">Scenario Timeline</button>");
        }
        if (hasComponent) {
            body.append("<button class=\"timeline-toggle\" onclick=\"toggle_component_diagram(this)\">Component Diagram</button>");
        }
        body.append("</div>");
        body.append("<div class=\"toolbar-right\">");
        body.append("<span class=\"details-radio\"><span class=\"details-radio-label\">Details:</span><button class=\"details-radio-btn\" data-state=\"expanded\" onclick=\"window._setReportDetails('expanded')\">Expand</button><button class=\"details-radio-btn\" data-state=\"collapsed\" onclick=\"window._setReportDetails('collapsed')\">Collapse</button><button class=\"details-radio-btn details-active\" data-state=\"truncated\" onclick=\"window._setReportDetails('truncated')\">Truncate</button>").append(truncateLinesSelect("window._setTruncateLines(this)")).append("<span class=\"truncate-lines-label\">lines</span></span>");
        body.append("<button class=\"details-radio-btn toggle-btn details-active\" data-toggle=\"headers\" data-shown=\"true\" onclick=\"window._toggleHeaders(this)\">Headers Shown</button>");
        body.append("</div>");
        body.append("</div>");
    }

    private static void appendTimeline(StringBuilder body, List<Feature> features, boolean hasDurations) {
        if (!hasDurations) {
            return;
        }
        List<Scenario> timeline = new ArrayList<>();
        for (Feature feature : features) {
            for (Scenario scenario : feature.scenarios()) {
                if (scenario.durationMs() > 0) {
                    timeline.add(scenario);
                }
            }
        }
        if (timeline.isEmpty()) {
            return;
        }
        timeline.sort(Comparator.comparingLong(Scenario::durationMs).reversed());
        double maxDuration = timeline.stream().mapToLong(Scenario::durationMs).max().orElse(0);
        body.append("<div id=\"scenario-timeline\" class=\"scenario-timeline\" style=\"display:none\">");
        body.append("<div class=\"timeline-header\">Scenario Timeline <span class=\"timeline-info\" title=\"The Scenario Timeline shows every test scenario ordered by duration (longest first). Each bar is proportional to the scenario's elapsed time, colour-coded by result: green = passed, red = failed, yellow = skipped. Use it to quickly spot slow tests, compare relative durations, and identify performance outliers across the entire test run.\">&#x1F6C8;</span></div>");
        for (Scenario scenario : timeline) {
            double widthPercent = maxDuration > 0 ? (scenario.durationMs() / maxDuration * 100) : 0;
            String statusClass = switch (scenario.status()) {
                case FAILED -> "timeline-bar-failed";
                case SKIPPED -> "timeline-bar-skipped";
                case INCONCLUSIVE -> "timeline-bar-bypassed";
                default -> "timeline-bar-passed";
            };
            String name = HtmlEscaper.encode(scenario.name());
            body.append("<div class=\"timeline-row\">")
                .append("<div class=\"timeline-label\" title=\"").append(name).append("\">").append(name).append("</div>")
                .append("<div class=\"timeline-track\"><div class=\"timeline-bar ").append(statusClass)
                .append("\" style=\"width:").append(f1(widthPercent)).append("%\" title=\"")
                .append(formatDurationBadge(scenario.durationMs())).append("\"></div></div>")
                .append("<div class=\"timeline-duration\">").append(formatDurationBadge(scenario.durationMs())).append("</div>")
                .append("</div>");
        }
        body.append("</div>");
    }

    private static int appendScenario(StringBuilder body, Feature feature, Scenario scenario,
                                      Map<String, String> diagramByTestId,
                                      Map<String, Set<String>> scenarioDependencies,
                                      Map<String, Set<String>> scenarioSearchTerms,
                                      Map<String, String> diagramData, int counter) {
        boolean failed = scenario.status() == ExecutionStatus.FAILED;
        boolean skipped = scenario.status() == ExecutionStatus.SKIPPED;

        Set<String> deps = scenarioDependencies.getOrDefault(scenario.testId(), Set.of());
        String depsAttr = deps.isEmpty() ? ""
            : " data-dependencies=\"" + HtmlEscaper.encode(String.join(",", new TreeSet<>(deps))) + "\"";
        String statusAttr = " data-status=\"" + scenario.status().displayName() + "\"";

        String durationAttr = "";
        String durationBadge = "";
        if (scenario.durationMs() > 0) {
            double ms = scenario.durationMs();
            durationAttr = " data-duration-ms=\"" + f0(ms) + "\"";
            String durationClass = ms < 2000 ? "duration-fast" : ms < 5000 ? "duration-moderate" : "duration-slow";
            durationBadge = " <span class=\"duration-badge " + durationClass + "\">" + formatDurationBadge(ms) + "</span>";
        }

        String categoriesAttr = scenario.categories().isEmpty() ? ""
            : " data-categories=\"" + HtmlEscaper.encode(String.join(",", scenario.categories())) + "\"";
        String labelsAttr = scenario.labels().isEmpty() ? ""
            : " data-labels=\"" + HtmlEscaper.encode(String.join(",", scenario.labels())) + "\"";

        String anchorId = scenarioAnchorId(scenario.name());

        List<String> searchParts = new ArrayList<>();
        searchParts.add(feature.displayName());
        searchParts.add(scenario.name());
        if (feature.description() != null) {
            searchParts.add(feature.description());
        }
        if (scenario.rule() != null) {
            searchParts.add(scenario.rule());
        }
        searchParts.addAll(feature.labels());
        searchParts.addAll(scenario.categories());
        searchParts.addAll(scenario.labels());
        if (failed && scenario.error() != null) {
            searchParts.add(scenario.error());
        }
        collectStepText(scenario, searchParts);
        searchParts.addAll(scenarioSearchTerms.getOrDefault(scenario.testId(), Set.of()));
        String searchAttr = " data-search=\""
            + HtmlEscaper.encode(String.join(" ", searchParts).toLowerCase(Locale.ROOT)) + "\"";

        String encodedName = HtmlEscaper.encode(scenario.name());
        String scenarioLabelsHtml = concatLabels(scenario.labels(),
            scenario.isHappyPath() ? "Happy Path" : null);
        String tooltip = scenarioTooltip(scenario.status());
        String happyPathClass = scenario.isHappyPath() ? " happy-path" : "";
        String happyPathLabel = scenario.isHappyPath() ? " <span class=\"label\">Happy Path</span>" : "";
        String summaryStatusClass = failed ? " failed" : skipped ? " skipped" : "";

        body.append("<details class=\"scenario").append(happyPathClass).append("\"")
            .append(depsAttr).append(statusAttr).append(searchAttr).append(durationAttr)
            .append(categoriesAttr).append(labelsAttr).append(" id=\"").append(anchorId)
            .append("\" tabindex=\"0\">").append(NL)
            .append("   <summary class=\"h3").append(summaryStatusClass).append("\" title=\"").append(tooltip)
            .append("\">").append(scenario.name()).append(happyPathLabel).append(scenarioLabelsHtml)
            .append(durationBadge)
            .append("<button class=\"copy-scenario-name\" title=\"Copy scenario name\" data-scenario-name=\"")
            .append(encodedName).append("\" onclick=\"copy_scenario_name(this, event)\">&#128203;</button>")
            .append("<a class=\"scenario-link\" href=\"#").append(anchorId)
            .append("\" title=\"Link to this scenario\" onclick=\"event.stopPropagation()\">&#128279;</a></summary>");

        if (failed) {
            // .NET interpolates the error + stack trace RAW (unescaped) into the <pre>. diffHtml is the
            // ErrorDiffParser expected/actual table — empty unless the message matches that shape.
            String diffHtml = "";
            ErrorDiffParser.DiffResult diff = ErrorDiffParser.tryParseExpectedActual(scenario.error());
            if (diff != null) {
                diffHtml = ErrorDiffParser.generateDiffHtml(diff.expected(), diff.actual());
            }
            body.append("<details class=\"failure-result\" open>").append(NL)
                .append("   <summary class=\"h4\">Failure Result</summary>").append(NL)
                .append("   <pre>").append(NL)
                .append("Failure Cause: ").append(nullToEmpty(scenario.error())).append(NL)
                .append(NL)
                .append(nullToEmpty(scenario.errorStackTrace())).append(NL)
                .append("   </pre>").append(NL)
                .append("   ").append(diffHtml).append(NL)
                .append("</details>");
        }

        appendBackgroundBlock(body, scenario.backgroundSteps());
        appendStepsBlock(body, scenario.steps());
        appendScenarioAttachments(body, scenario.attachments());

        String diagram = diagramByTestId.get(scenario.testId());
        boolean hasSequenceDiagrams = diagram != null;
        if (hasSequenceDiagrams) {
            body.append("<details class=\"example-diagrams\" open>");
            body.append("<summary class=\"h4\">Sequence Diagrams</summary>");
            body.append("<div class=\"diagram-toggle\">");
            body.append("<span class=\"diagram-toggle-spacer\"></span><span class=\"details-radio\"><span class=\"details-radio-label\">Details:</span><button class=\"details-radio-btn\" data-state=\"expanded\" onclick=\"window._setAllNotes(this,'expanded')\">Expand</button><button class=\"details-radio-btn\" data-state=\"collapsed\" onclick=\"window._setAllNotes(this,'collapsed')\">Collapse</button><button class=\"details-radio-btn details-active\" data-state=\"truncated\" onclick=\"window._setAllNotes(this,'truncated')\">Truncate</button>")
                .append(truncateLinesSelect("window._setScenarioTruncateLines(this)"))
                .append("<span class=\"truncate-lines-label\">lines</span></span><button class=\"details-radio-btn toggle-btn details-active\" data-toggle=\"headers\" data-shown=\"true\" onclick=\"window._toggleScenarioHeaders(this)\">Headers Shown</button>");
            body.append("</div>");

            String diagramId = "puml-" + (counter++);
            diagramData.put(diagramId, compressToBase64(diagram));
            body.append("<div class=\"plantuml-browser\" id=\"").append(diagramId)
                .append("\" data-diagram-type=\"plantuml\"></div>");
            body.append("</details>");
        }
        body.append("</details>");
        return counter;
    }

    private static void appendScenarioAttachments(StringBuilder body, List<FileAttachment> attachments) {
        if (attachments.isEmpty()) {
            return;
        }
        body.append("<div class=\"scenario-attachments\">");
        for (FileAttachment att : attachments) {
            String relPath = HtmlEscaper.encode(att.relativePath());
            String name = HtmlEscaper.encode(att.name());
            if (isImageAttachment(att.name())) {
                body.append("<a class=\"attachment-image-link\" href=\"").append(relPath)
                    .append("\" target=\"_blank\"><img class=\"attachment-image\" src=\"").append(relPath)
                    .append("\" alt=\"").append(name).append("\" /></a>");
            } else {
                body.append("<a class=\"step-attachment\" href=\"").append(relPath).append("\">").append(name).append("</a>");
            }
        }
        body.append("</div>");
    }

    /** Ports .NET {@code RenderParameterizedGroup} for the representable subset (ScalarColumns/Fallback,
     *  no flat view / complex-object cells / per-example diagrams — see {@link ParameterGrouper}). */
    private static void appendParameterizedGroup(StringBuilder body, Feature feature,
                                                 ParameterGrouper.ParameterizedGroup group, String prefix,
                                                 Map<String, Set<String>> scenarioDependencies,
                                                 Map<String, Set<String>> scenarioSearchTerms) {
        List<Scenario> scenarios = group.scenarios();
        boolean hasFailure = scenarios.stream().anyMatch(s -> s.status() == ExecutionStatus.FAILED);
        boolean hasSkipped = scenarios.stream().anyMatch(s -> s.status() == ExecutionStatus.SKIPPED);
        String overallStatus = hasFailure ? "Failed" : hasSkipped ? "Skipped"
            : scenarios.stream().anyMatch(s -> s.status() == ExecutionStatus.INCONCLUSIVE) ? "Bypassed" : "Passed";

        List<String> searchParts = new ArrayList<>();
        searchParts.add(group.groupDisplayName());
        searchParts.add(feature.displayName());
        if (feature.description() != null) {
            searchParts.add(feature.description());
        }
        searchParts.addAll(feature.labels());
        for (Scenario s : scenarios) {
            searchParts.add(s.name());
            if (s.rule() != null) {
                searchParts.add(s.rule());
            }
            searchParts.addAll(s.categories());
            searchParts.addAll(s.labels());
            if (s.error() != null) {
                searchParts.add(s.error());
            }
            collectStepText(s.steps(), searchParts);
            searchParts.addAll(scenarioSearchTerms.getOrDefault(s.testId(), Set.of()));
        }
        String searchAttr = " data-search=\""
            + HtmlEscaper.encode(String.join(" ", searchParts).toLowerCase(Locale.ROOT)) + "\"";

        Set<String> categories = new LinkedHashSet<>();
        Set<String> labels = new LinkedHashSet<>();
        Set<String> deps = new TreeSet<>();
        for (Scenario s : scenarios) {
            categories.addAll(s.categories());
            labels.addAll(s.labels());
            deps.addAll(scenarioDependencies.getOrDefault(s.testId(), Set.of()));
        }
        String categoriesAttr = categories.isEmpty() ? ""
            : " data-categories=\"" + HtmlEscaper.encode(String.join(",", categories)) + "\"";
        String labelsAttr = labels.isEmpty() ? ""
            : " data-labels=\"" + HtmlEscaper.encode(String.join(",", labels)) + "\"";
        String depsAttr = deps.isEmpty() ? ""
            : " data-dependencies=\"" + HtmlEscaper.encode(String.join(",", deps)) + "\"";

        long totalMs = scenarios.stream().filter(s -> s.durationMs() > 0).mapToLong(Scenario::durationMs).sum();
        String durationAttr = totalMs > 0 ? " data-duration-ms=\"" + f0(totalMs) + "\"" : "";
        String durationBadge = totalMs > 0
            ? " <span class=\"duration-badge " + (totalMs < 2000 ? "duration-fast" : totalMs < 5000 ? "duration-moderate" : "duration-slow")
                + "\">" + formatDurationBadge(totalMs) + "</span>"
            : "";

        long passCount = scenarios.stream().filter(s -> s.status() == ExecutionStatus.PASSED).count();
        long failCount = scenarios.stream().filter(s -> s.status() == ExecutionStatus.FAILED).count();
        long skipCount = scenarios.stream()
            .filter(s -> s.status() == ExecutionStatus.SKIPPED || s.status() == ExecutionStatus.INCONCLUSIVE).count();
        List<String> summaryParts = new ArrayList<>();
        if (failCount > 0) {
            summaryParts.add(failCount + " failed");
        }
        if (skipCount > 0) {
            summaryParts.add(skipCount + " skipped");
        }
        summaryParts.add(passCount + "/" + scenarios.size() + " passed");
        String summaryText = " <span class=\"label\">" + String.join(", ", summaryParts) + "</span>";

        String anchorId = scenarioAnchorId(group.groupDisplayName());
        String encodedGroupName = HtmlEscaper.encode(group.groupDisplayName());
        boolean isGroupHappyPath = scenarios.stream().anyMatch(Scenario::isHappyPath);
        String happyPathClass = isGroupHappyPath ? " happy-path" : "";
        String happyPathBadge = isGroupHappyPath ? " <span class=\"label\">Happy Path</span>" : "";

        body.append("<details class=\"scenario scenario-parameterized").append(happyPathClass)
            .append("\" data-status=\"").append(overallStatus).append("\"").append(depsAttr).append(searchAttr)
            .append(durationAttr).append(categoriesAttr).append(labelsAttr).append(" id=\"").append(anchorId)
            .append("\" tabindex=\"0\">");
        body.append("<summary class=\"h3").append(hasFailure ? " failed" : hasSkipped ? " skipped" : "")
            .append("\">").append(encodedGroupName).append(happyPathBadge).append(summaryText).append(durationBadge)
            .append("<button class=\"copy-scenario-name\" title=\"Copy scenario name\" data-scenario-name=\"")
            .append(encodedGroupName).append("\" onclick=\"copy_scenario_name(this, event)\">&#128203;</button>")
            .append("<a class=\"scenario-link\" href=\"#").append(anchorId)
            .append("\" title=\"Link to this scenario\" onclick=\"event.stopPropagation()\">&#128279;</a></summary>");

        boolean scalarColumns = group.rule() == ParameterGrouper.Rule.SCALAR_COLUMNS && !group.parameterNames().isEmpty();
        body.append("<table class=\"param-test-table\" data-prefix=\"").append(prefix).append("\"><thead>");
        if (scalarColumns) {
            body.append("<tr><th rowspan=\"2\" style=\"width:2.5em\">#</th>");
            body.append("<th colspan=\"").append(group.parameterNames().size())
                .append("\" class=\"master-header\">Input Parameters</th>");
            body.append("<th rowspan=\"2\" style=\"width:5em\">Status</th>");
            body.append("<th rowspan=\"2\" style=\"width:5.5em\">Duration</th></tr>");
            body.append("<tr>");
            for (String name : group.parameterNames()) {
                body.append("<th class=\"sub-header\">").append(HtmlEscaper.encode(Humanize.titleize(name))).append("</th>");
            }
            body.append("</tr>");
        } else {
            body.append("<tr><th style=\"width:2.5em\">#</th><th>Test Case</th>"
                + "<th style=\"width:5em\">Status</th><th style=\"width:5.5em\">Duration</th></tr>");
        }
        body.append("</thead><tbody>");

        for (int ri = 0; ri < scenarios.size(); ri++) {
            Scenario s = scenarios.get(ri);
            String rowStatusClass = paramRowStatusClass(s.status());
            String activeClass = ri == 0 ? " row-active" : "";

            List<String> rowSearch = new ArrayList<>();
            rowSearch.add(s.name());
            rowSearch.add(feature.displayName());
            if (feature.description() != null) {
                rowSearch.add(feature.description());
            }
            rowSearch.addAll(feature.labels());
            rowSearch.addAll(s.categories());
            rowSearch.addAll(s.labels());
            if (s.error() != null) {
                rowSearch.add(s.error());
            }
            collectStepText(s.steps(), rowSearch);
            rowSearch.addAll(scenarioSearchTerms.getOrDefault(s.testId(), Set.of()));
            String rowSearchAttr = " data-row-search=\""
                + HtmlEscaper.encode(String.join(" ", rowSearch).toLowerCase(Locale.ROOT)) + "\"";

            String rowAnchor = scenarioAnchorId(s.name());
            body.append("<tr class=\"").append(rowStatusClass).append(activeClass).append("\" data-row-idx=\"")
                .append(ri).append("\" id=\"").append(rowAnchor).append("\" data-scenario-id=\"").append(rowAnchor)
                .append("\"").append(rowSearchAttr).append(" onclick=\"selectRow(this,'").append(prefix).append("')\">");
            body.append("<td>").append(ri + 1).append("</td>");
            if (scalarColumns) {
                for (String name : group.parameterNames()) {
                    // No ExampleRawValues in the Java model (the .NET reflection R3/R4 path is not
                    // cross-runtime byte-parity-able), so each cell takes the string-based R3/R4 path:
                    // a record ToString() shape renders as a cell-subtable / param-expand, else scalar.
                    String val = s.exampleValues() == null ? "" : s.exampleValues().getOrDefault(name, "");
                    StringBuilder tdBody = new StringBuilder();
                    if (ParameterValueRenderer.tryRenderFromParsedString(tdBody, val)) {
                        body.append("<td>").append(tdBody).append("</td>");
                    } else {
                        body.append("<td class=\"mono\">").append(formatDisplayValue(val)).append("</td>");
                    }
                }
            } else {
                String displayText = s.exampleDisplayName() != null ? s.exampleDisplayName() : s.name();
                body.append("<td class=\"mono\">").append(HtmlEscaper.encode(displayText)).append("</td>");
            }
            String rowDuration = s.durationMs() > 0 ? formatDurationBadge(s.durationMs()) : "";
            body.append("<td><span class=\"status-badge ").append(paramBadgeClass(s.status())).append("\">")
                .append(paramBadgeText(s.status())).append("</span></td>");
            body.append("<td class=\"mono\">").append(rowDuration).append("</td>");
            body.append("</tr>");
        }
        body.append("</tbody></table>");

        // Detail panels (steps / attachments / failure) — one per example, first shown.
        boolean hasAnyDetail = scenarios.stream()
            .anyMatch(s -> !s.steps().isEmpty() || s.status() == ExecutionStatus.FAILED);
        if (hasAnyDetail) {
            body.append("<div class=\"param-detail-panels\">");
            for (int ri = 0; ri < scenarios.size(); ri++) {
                Scenario s = scenarios.get(ri);
                String display = ri == 0 ? "" : " style=\"display:none\"";
                body.append("<div class=\"param-detail-panel\" id=\"").append(prefix).append("-detail-")
                    .append(ri).append("\"").append(display).append(">");
                appendBackgroundBlock(body, s.backgroundSteps());
                appendStepsBlock(body, s.steps());
                appendScenarioAttachments(body, s.attachments());
                if (s.status() == ExecutionStatus.FAILED) {
                    String diffHtml = "";
                    ErrorDiffParser.DiffResult diff = ErrorDiffParser.tryParseExpectedActual(s.error());
                    if (diff != null) {
                        diffHtml = ErrorDiffParser.generateDiffHtml(diff.expected(), diff.actual());
                    }
                    // Compact failure-result form used inside the parameterized detail panel.
                    body.append("<details class=\"failure-result\" open><summary class=\"h4\">Failure Result</summary><pre>");
                    if (s.error() != null) {
                        body.append("Failure Cause: ").append(s.error()).append("\n\n");
                    }
                    if (s.errorStackTrace() != null) {
                        body.append(s.errorStackTrace());
                    }
                    body.append("</pre>").append(diffHtml).append("</details>");
                }
                body.append("</div>");
            }
            body.append("</div>");
        }
        // (Per-example sequence diagrams / whole-test-flow are out of scope — see ParameterGrouper.)

        body.append("</details>");
    }

    private static String paramRowStatusClass(ExecutionStatus status) {
        return switch (status) {
            case PASSED -> "row-passed";
            case FAILED -> "row-failed";
            case SKIPPED -> "row-skipped";
            case INCONCLUSIVE -> "row-bypassed";
        };
    }

    private static String paramBadgeClass(ExecutionStatus status) {
        return switch (status) {
            case PASSED -> "badge-pass";
            case FAILED -> "badge-fail";
            case SKIPPED -> "badge-skip";
            case INCONCLUSIVE -> "badge-bypass";
        };
    }

    private static String paramBadgeText(ExecutionStatus status) {
        return switch (status) {
            case PASSED -> "Passed";
            case FAILED -> "Failed";
            case SKIPPED -> "Skipped";
            case INCONCLUSIVE -> "Bypassed";
        };
    }

    /** Mirrors .NET {@code FormatDisplayValue}. */
    private static String formatDisplayValue(String value) {
        if (value == null || value.equals("null")) {
            return "<pre>null</pre>";
        }
        if (!value.isEmpty() && value.strip().isEmpty()) {
            return "<pre>" + HtmlEscaper.encode(value) + "</pre>";
        }
        return HtmlEscaper.encode(value);
    }

    private static String truncateLinesSelect(String onchange) {
        return "<select class=\"truncate-lines-select\" onchange=\"" + onchange + "\">"
            + "<option value=\"3\">3</option><option value=\"4\">4</option><option value=\"5\">5</option>"
            + "<option value=\"10\">10</option><option value=\"15\">15</option><option value=\"20\">20</option>"
            + "<option value=\"25\">25</option><option value=\"30\">30</option><option value=\"35\">35</option>"
            + "<option value=\"40\" selected>40</option><option value=\"50\">50</option><option value=\"60\">60</option>"
            + "<option value=\"80\">80</option><option value=\"100\">100</option></select>";
    }

    private static String concatLabels(List<String> labels, String excludeHappyPath) {
        StringBuilder sb = new StringBuilder();
        for (String label : labels) {
            if (excludeHappyPath != null && label.equalsIgnoreCase(excludeHappyPath)) {
                continue;
            }
            sb.append(" <span class=\"label\">").append(HtmlEscaper.encode(label)).append("</span>");
        }
        return sb.toString();
    }

    private static void collectStepText(Scenario scenario, List<String> parts) {
        collectStepText(scenario.steps(), parts); // .NET CollectStepText: Steps only (not background), recursing sub-steps
    }

    private static void collectStepText(List<ScenarioStep> steps, List<String> parts) {
        for (ScenarioStep step : steps) {
            parts.add(step.text());
            collectStepText(step.subSteps(), parts);
        }
    }

    /** Ports .NET {@code RenderStep} for the fields the Java {@link ScenarioStep} model carries
     *  (keyword/text/status/duration/sub-steps); the .NET-only inline/tabular params, doc-strings and
     *  comments are not representable in the Java model and never reach here. */
    private static void appendBackgroundBlock(StringBuilder body, List<ScenarioStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        body.append("<details class=\"scenario-background\"><summary class=\"h4\">Background Steps</summary>");
        for (ScenarioStep step : steps) {
            appendStep(body, step, null, false); // background: no combined-table suppression
        }
        body.append("</details>");
    }

    /** The {@code scenario-steps} block: each step (with combined-table suppression for tabular
     *  assertion params), then the combined setup+assertion table when {@link #shouldRenderCombinedTable}. */
    private static void appendStepsBlock(StringBuilder body, List<ScenarioStep> steps) {
        if (steps.isEmpty()) {
            return;
        }
        body.append("<details class=\"scenario-steps\" open><summary class=\"h4\">Steps</summary>");
        boolean renderCombined = shouldRenderCombinedTable(steps);
        boolean afterThen = false;
        for (ScenarioStep step : steps) {
            String kw = step.keyword() == null ? null : step.keyword().trim();
            if ("Then".equalsIgnoreCase(kw)) {
                afterThen = true;
            } else if ("Given".equalsIgnoreCase(kw) || "When".equalsIgnoreCase(kw)) {
                afterThen = false;
            }
            appendStep(body, step, null, renderCombined && afterThen);
        }
        if (renderCombined) {
            appendCombinedTabularParameters(body, steps);
        }
        body.append("</details>");
    }

    /** Port of .NET ShouldRenderCombinedTable: combine when there is both a setup (pre-Then) and an
     *  assertion (Then) tabular param, linked by IsLinkedOutput, shared key columns, or equal row counts. */
    private static boolean shouldRenderCombinedTable(List<ScenarioStep> steps) {
        boolean afterThen = false;
        TabularParameterValue setupTable = null;
        TabularParameterValue assertionTable = null;
        for (ScenarioStep step : steps) {
            String kw = step.keyword() == null ? null : step.keyword().trim();
            if ("Then".equalsIgnoreCase(kw)) {
                afterThen = true;
            } else if ("Given".equalsIgnoreCase(kw) || "When".equalsIgnoreCase(kw)) {
                afterThen = false;
            }
            TabularParameterValue tab = firstTabular(step);
            if (tab != null) {
                if (afterThen && assertionTable == null) {
                    assertionTable = tab;
                } else if (!afterThen && setupTable == null) {
                    setupTable = tab;
                }
            }
        }
        if (setupTable == null || assertionTable == null) {
            return false;
        }
        if (assertionTable.isLinkedOutput()) {
            return true;
        }
        Set<String> outputKeyNames = new LinkedHashSet<>();
        for (TabularParameterValue.TabularColumn c : assertionTable.columns()) {
            if (c.isKey()) {
                outputKeyNames.add(c.name());
            }
        }
        if (!outputKeyNames.isEmpty()
            && setupTable.columns().stream().anyMatch(c -> outputKeyNames.contains(c.name()))) {
            return true;
        }
        return setupTable.rows().size() > 1 && setupTable.rows().size() == assertionTable.rows().size();
    }

    private static TabularParameterValue firstTabular(ScenarioStep step) {
        for (StepParameter p : step.parameters()) {
            if (p.kind() == StepParameter.Kind.TABULAR && p.tabularValue() != null) {
                return p.tabularValue();
            }
        }
        return null;
    }

    /** Port of .NET RenderCombinedTabularParameters: a single table aligning the input (setup) tables
     *  against the output (assertion) table, key-aligned by shared key columns when available. */
    private static void appendCombinedTabularParameters(StringBuilder body, List<ScenarioStep> steps) {
        record Named(String name, TabularParameterValue table) {
        }
        List<Named> namedParams = new ArrayList<>();
        for (ScenarioStep step : steps) {
            for (StepParameter p : step.parameters()) {
                if (p.kind() == StepParameter.Kind.TABULAR && p.tabularValue() != null) {
                    namedParams.add(new Named(p.name(), p.tabularValue()));
                }
            }
        }
        if (namedParams.isEmpty()) {
            return;
        }
        boolean hasSeparator = namedParams.size() > 1;
        List<Named> inputParams = hasSeparator ? namedParams.subList(0, namedParams.size() - 1) : namedParams;
        Named outputParam = hasSeparator ? namedParams.get(namedParams.size() - 1) : null;

        Set<String> sharedKeyNames = new LinkedHashSet<>();
        boolean useKeyAlignment = false;
        if (outputParam != null && !outputParam.table().isLinkedOutput()) {
            Set<String> outputKeyNames = new LinkedHashSet<>();
            for (TabularParameterValue.TabularColumn c : outputParam.table().columns()) {
                if (c.isKey()) {
                    outputKeyNames.add(c.name());
                }
            }
            if (!outputKeyNames.isEmpty()) {
                for (Named ip : inputParams) {
                    for (TabularParameterValue.TabularColumn c : ip.table().columns()) {
                        if (outputKeyNames.contains(c.name())) {
                            sharedKeyNames.add(c.name());
                        }
                    }
                }
                useKeyAlignment = !sharedKeyNames.isEmpty();
            }
        }

        int[] inputRowOrder = null;
        int maxRows;
        if (useKeyAlignment && outputParam != null && !inputParams.isEmpty()) {
            Named primaryInput = inputParams.get(0);
            List<Integer> keyColIn = new ArrayList<>();
            List<Integer> keyColOut = new ArrayList<>();
            for (String k : sharedKeyNames) {
                int i1 = colIndex(primaryInput.table().columns(), k);
                if (i1 >= 0) {
                    keyColIn.add(i1);
                }
                int i2 = colIndex(outputParam.table().columns(), k);
                if (i2 >= 0) {
                    keyColOut.add(i2);
                }
            }
            Map<String, Integer> inputKeyLookup = new HashMap<>();
            for (int i = 0; i < primaryInput.table().rows().size(); i++) {
                inputKeyLookup.putIfAbsent(rowKey(primaryInput.table().rows().get(i), keyColIn), i);
            }
            List<Integer> aligned = new ArrayList<>();
            Set<Integer> matched = new HashSet<>();
            for (TabularParameterValue.TabularRow outRow : outputParam.table().rows()) {
                Integer idx = inputKeyLookup.get(rowKey(outRow, keyColOut));
                if (idx != null && matched.add(idx)) {
                    aligned.add(idx);
                } else {
                    aligned.add(-1);
                }
            }
            for (int i = 0; i < primaryInput.table().rows().size(); i++) {
                if (!matched.contains(i)) {
                    aligned.add(i);
                }
            }
            inputRowOrder = aligned.stream().mapToInt(Integer::intValue).toArray();
            maxRows = inputRowOrder.length;
        } else {
            maxRows = namedParams.stream().mapToInt(n -> n.table().rows().size()).max().orElse(0);
        }

        boolean showRowIndicator = namedParams.stream()
            .anyMatch(n -> n.table().rows().stream().anyMatch(r -> r.type() != TableRowType.MATCHING));
        body.append(showRowIndicator
            ? "<div class=\"step-param-combined-table\"><table><thead><tr><th></th>"
            : "<div class=\"step-param-combined-table\"><table><thead><tr>");
        for (Named param : inputParams) {
            appendCombinedHeaders(body, param.name(), param.table());
        }
        if (hasSeparator) {
            body.append("<th class=\"combined-separator\">=</th>");
            appendCombinedHeaders(body, outputParam.name(), outputParam.table());
        }
        body.append("</tr></thead><tbody>");

        for (int ri = 0; ri < maxRows; ri++) {
            int inputRi = inputRowOrder != null ? inputRowOrder[ri] : ri;
            int outputRi = inputRowOrder != null
                ? (ri < (outputParam != null ? outputParam.table().rows().size() : 0) ? ri : -1)
                : ri;
            TableRowType rowType;
            if (outputParam != null && outputRi >= 0 && outputRi < outputParam.table().rows().size()) {
                rowType = outputParam.table().rows().get(outputRi).type();
            } else if (inputRi >= 0 && inputParams.get(0).table().rows().size() > inputRi) {
                rowType = inputParams.get(0).table().rows().get(inputRi).type();
            } else {
                rowType = TableRowType.MATCHING;
            }
            String rowIndicator = switch (rowType) {
                case MATCHING -> "=";
                case SURPLUS -> "+";
                case MISSING -> "-";
            };
            String rowClass = "row-" + rowType.name().toLowerCase(Locale.ROOT);
            body.append(showRowIndicator
                ? "<tr class=\"" + rowClass + "\"><td>" + rowIndicator + "</td>"
                : "<tr class=\"" + rowClass + "\">");
            for (Named param : inputParams) {
                appendCombinedCells(body, param.name(), param.table(), inputRi);
            }
            if (hasSeparator) {
                body.append("<td class=\"combined-separator\"></td>");
                appendCombinedCells(body, outputParam.name(), outputParam.table(), outputRi);
            }
            body.append("</tr>");
        }
        body.append("</tbody></table></div>");
    }

    private static void appendCombinedHeaders(StringBuilder body, String name, TabularParameterValue table) {
        String enc = HtmlEscaper.encode(name);
        for (TabularParameterValue.TabularColumn col : table.columns()) {
            body.append("<th data-param=\"").append(enc).append("\"").append(col.isKey() ? " class=\"key\"" : "")
                .append(">").append(HtmlEscaper.encode(col.name())).append("</th>");
        }
    }

    private static void appendCombinedCells(StringBuilder body, String name, TabularParameterValue table, int rowIdx) {
        String enc = HtmlEscaper.encode(name);
        if (rowIdx >= 0 && rowIdx < table.rows().size()) {
            for (TabularParameterValue.TabularCell cell : table.rows().get(rowIdx).values()) {
                appendCell(body, cell, enc);
            }
        } else {
            for (int ci = 0; ci < table.columns().size(); ci++) {
                body.append("<td data-param=\"").append(enc).append("\"></td>");
            }
        }
    }

    private static int colIndex(List<TabularParameterValue.TabularColumn> cols, String name) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String rowKey(TabularParameterValue.TabularRow row, List<Integer> keyColIndices) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < keyColIndices.size(); j++) {
            if (j > 0) {
                sb.append('\0');
            }
            int ci = keyColIndices.get(j);
            sb.append(ci < row.values().size() ? row.values().get(ci).value() : "");
        }
        return sb.toString();
    }

    private static void appendCell(StringBuilder body, TabularParameterValue.TabularCell cell, String dataParam) {
        String cellDisplay = cell.expectation() != null && cell.status() == VerificationStatus.FAILURE
            ? formatDisplayValue(cell.value()) + "/" + formatDisplayValue(cell.expectation())
            : formatDisplayValue(cell.value());
        String dataParamAttr = dataParam != null ? " data-param=\"" + dataParam + "\"" : "";
        body.append("<td class=\"").append(cellStatusClass(cell.status())).append("\"").append(dataParamAttr)
            .append(">").append(cellDisplay).append("</td>");
    }

    private static void appendStep(StringBuilder body, ScenarioStep step, String numberPrefix,
                                   boolean skipTabularInline) {
        ExecutionStatus status = step.status();
        boolean hasSub = !step.subSteps().isEmpty();

        if (hasSub) {
            body.append(hasAnyFailed(step)
                ? "<details class=\"step step-collapsible\" open>"
                : "<details class=\"step step-collapsible\">");
            body.append("<summary>");
        } else {
            body.append("<div class=\"step\">");
        }

        if (numberPrefix != null) {
            body.append("<span class=\"step-number\">").append(numberPrefix).append("</span>");
        }
        if (status != null) {
            body.append("<span class=\"step-status ").append(stepStatusClass(step)).append("\" title=\"")
                .append(stepStatusTooltip(step)).append("\">").append(stepStatusIcon(status)).append("</span>");
        }
        if (step.keyword() != null) {
            body.append("<span class=\"step-keyword\">").append(HtmlEscaper.encode(step.keyword())).append("</span> ");
        }
        if (!step.textSegments().isEmpty()) {
            appendStepTextSegments(body, step);
        } else {
            body.append("<span class=\"step-text\">").append(HtmlEscaper.encode(step.text())).append("</span>");
        }
        if (step.durationMs() != null) {
            body.append(" <span class=\"step-duration\">(").append(formatDurationBadge(step.durationMs())).append(")</span>");
        }
        for (String comment : step.comments()) {
            body.append("<div class=\"step-comment\">").append(HtmlEscaper.encode(comment)).append("</div>");
        }
        for (FileAttachment att : step.attachments()) {
            String relPath = HtmlEscaper.encode(att.relativePath());
            String name = HtmlEscaper.encode(att.name());
            if (isImageAttachment(att.name())) {
                body.append("<a class=\"attachment-image-link\" href=\"").append(relPath)
                    .append("\" onclick=\"openLightbox(event, this)\"><img class=\"attachment-image\" src=\"")
                    .append(relPath).append("\" alt=\"").append(name).append("\" /></a>");
                body.append("<span class=\"attachment-image-name\">").append(name).append("</span>");
            } else {
                body.append("<a class=\"step-attachment\" href=\"").append(relPath).append("\">").append(name).append("</a>");
            }
        }
        for (StepParameter param : step.parameters()) {
            if (skipTabularInline && param.kind() == StepParameter.Kind.TABULAR) {
                continue; // rendered in the combined setup+assertion table instead
            }
            if (!step.textSegments().isEmpty() && param.kind() == StepParameter.Kind.INLINE) {
                continue; // already rendered inline in the text segments
            }
            appendParameter(body, param);
        }
        if (step.docString() != null) {
            String codeClass = step.docStringMediaType() != null
                ? " class=\"language-" + HtmlEscaper.encode(step.docStringMediaType()) + "\"" : "";
            body.append("<pre class=\"step-docstring\"><code").append(codeClass).append(">")
                .append(HtmlEscaper.encode(step.docString())).append("</code></pre>");
        }

        if (hasSub) {
            body.append("</summary>");
            body.append("<div class=\"sub-steps\">");
            for (int i = 0; i < step.subSteps().size(); i++) {
                String subPrefix = numberPrefix != null ? numberPrefix + (i + 1) + "." : null;
                appendStep(body, step.subSteps().get(i), subPrefix, true);
            }
            body.append("</div>");
            body.append("</details>");
        } else {
            body.append("</div>");
        }
    }

    /** Ports .NET RenderStep's structured-text-segment branch: literal prose interleaved with inline
     *  parameter values (highlighted by verification status). The tabular/tree reference path falls
     *  back to its formatted value / plain text until the tabular-parameter feature is ported. */
    private static void appendStepTextSegments(StringBuilder body, ScenarioStep step) {
        body.append("<span class=\"step-text\">");
        for (StepTextSegment seg : step.textSegments()) {
            if (seg.parameter() != null) {
                String statusClass = paramStatusClass(seg.parameter().status());
                String display = seg.parameter().expectation() != null
                    ? formatDisplayValue(seg.parameter().value()) + "/" + formatDisplayValue(seg.parameter().expectation())
                    : formatDisplayValue(seg.parameter().value());
                String titleAttr = seg.parameterName() != null
                    ? " title=\"" + HtmlEscaper.encode(seg.parameterName()) + "\"" : "";
                body.append("<span class=\"step-param-inline ").append(statusClass).append("\"")
                    .append(titleAttr).append(">").append(display).append("</span>");
            } else if (seg.tableReference() != null) {
                appendTableReferenceSegment(body, step, seg);
            } else if (seg.text() != null) {
                body.append(HtmlEscaper.encode(seg.text()));
            }
        }
        body.append("</span>");
    }

    /** Port of the .NET RenderStep text-segment table-reference branch: the reference resolves against
     *  the step's StepParameters — complex inline → an inline summary span or an expandable button;
     *  simple inline → a span; tabular/tree → a button; otherwise the formatted value or the plain text. */
    private static void appendTableReferenceSegment(StringBuilder body, ScenarioStep step, StepTextSegment seg) {
        StepParameter matching = findParam(step.parameters(), seg.tableReference());
        String ref = HtmlEscaper.encode(seg.tableReference());
        if (matching != null && matching.kind() == StepParameter.Kind.INLINE && matching.inlineValue() != null
                && ParameterParser.isComplexObjectString(matching.inlineValue().value())) {
            String complexVal = matching.inlineValue().value();
            if (ParameterParser.isSmallComplexValue(complexVal)) {
                String inlineDisplay = ParameterParser.formatComplexValueInline(complexVal);
                body.append("<span class=\"step-param-inline param-na\" title=\"").append(ref).append("\">")
                    .append(HtmlEscaper.encode(inlineDisplay != null ? inlineDisplay : complexVal)).append("</span>");
            } else {
                String json = ParameterParser.formatComplexValueAsJson(complexVal);
                body.append("</span>");
                body.append("<button class=\"step-table-ref\" onclick=\"toggle_table_ref(this)\" data-param=\"")
                    .append(ref).append("\" data-value=\"").append(HtmlEscaper.encode(json != null ? json : complexVal))
                    .append("\">").append(ref).append("</button>");
                body.append("<span class=\"step-text\">");
            }
        } else if (matching != null && matching.kind() == StepParameter.Kind.INLINE && matching.inlineValue() != null) {
            body.append("<span class=\"step-param-inline param-na\" title=\"").append(ref).append("\">")
                .append(formatDisplayValue(matching.inlineValue().value())).append("</span>");
        } else if (matching != null
                && (matching.kind() == StepParameter.Kind.TABULAR || matching.kind() == StepParameter.Kind.TREE)) {
            body.append("</span>");
            body.append("<button class=\"step-table-ref\" onclick=\"toggle_table_ref(this)\" data-param=\"")
                .append(ref).append("\">").append(ref).append("</button>");
            body.append("<span class=\"step-text\">");
        } else if (seg.tableReferenceFormattedValue() != null) {
            body.append("<span class=\"step-param-inline param-na\" title=\"").append(ref).append("\">")
                .append(HtmlEscaper.encode(seg.tableReferenceFormattedValue())).append("</span>");
        } else {
            body.append(ref);
        }
    }

    private static StepParameter findParam(List<StepParameter> params, String name) {
        for (StepParameter p : params) {
            if (p.name() != null && p.name().equals(name)) {
                return p;
            }
        }
        return null;
    }

    /** Ports .NET {@code RenderParameter}: inline → highlighted span; tabular → step-param-table;
     *  tree → step-param-tree. */
    private static void appendParameter(StringBuilder body, StepParameter param) {
        switch (param.kind()) {
            case INLINE -> {
                if (param.inlineValue() != null) {
                    InlineParameterValue iv = param.inlineValue();
                    String display = iv.expectation() != null
                        ? formatDisplayValue(iv.value()) + "/" + formatDisplayValue(iv.expectation())
                        : formatDisplayValue(iv.value());
                    body.append("<span class=\"step-param-inline ").append(paramStatusClass(iv.status()))
                        .append("\" title=\"").append(HtmlEscaper.encode(param.name())).append("\">")
                        .append(display).append("</span>");
                }
            }
            case TABULAR -> {
                if (param.tabularValue() != null) {
                    appendTabularParameter(body, param.name(), param.tabularValue());
                }
            }
            case TREE -> {
                if (param.treeValue() != null) {
                    body.append("<div class=\"step-param-tree\">");
                    appendTreeNode(body, param.treeValue().root());
                    body.append("</div>");
                }
            }
        }
    }

    private static void appendTabularParameter(StringBuilder body, String name, TabularParameterValue tv) {
        List<String> colNames = tv.columns().stream().map(TabularParameterValue.TabularColumn::name).toList();
        body.append("<div class=\"step-param-table\" data-param=\"").append(HtmlEscaper.encode(name))
            .append("\" data-columns=\"").append(HtmlEscaper.encode(String.join(",", colNames))).append("\">");
        boolean showRowIndicator = tv.rows().stream().anyMatch(r -> r.type() != TableRowType.MATCHING);
        body.append(showRowIndicator ? "<table><thead><tr><th></th>" : "<table><thead><tr>");
        for (TabularParameterValue.TabularColumn col : tv.columns()) {
            body.append("<th").append(col.isKey() ? " class=\"key\"" : "").append(">")
                .append(HtmlEscaper.encode(col.name())).append("</th>");
        }
        body.append("</tr></thead><tbody>");
        for (TabularParameterValue.TabularRow row : tv.rows()) {
            String rowIndicator = switch (row.type()) {
                case MATCHING -> "=";
                case SURPLUS -> "+";
                case MISSING -> "-";
            };
            String rowClass = "row-" + row.type().name().toLowerCase(Locale.ROOT);
            body.append(showRowIndicator
                ? "<tr class=\"" + rowClass + "\"><td>" + rowIndicator + "</td>"
                : "<tr class=\"" + rowClass + "\">");
            for (TabularParameterValue.TabularCell cell : row.values()) {
                String cellDisplay = cell.expectation() != null && cell.status() == VerificationStatus.FAILURE
                    ? formatDisplayValue(cell.value()) + "/" + formatDisplayValue(cell.expectation())
                    : formatDisplayValue(cell.value());
                body.append("<td class=\"").append(cellStatusClass(cell.status())).append("\">")
                    .append(cellDisplay).append("</td>");
            }
            body.append("</tr>");
        }
        body.append("</tbody></table></div>");
    }

    private static void appendTreeNode(StringBuilder body, TreeParameterValue.TreeNode node) {
        String valueDisplay = node.expectation() != null && node.status() == VerificationStatus.FAILURE
            ? formatDisplayValue(node.value()) + "/" + formatDisplayValue(node.expectation())
            : formatDisplayValue(node.value());
        body.append("<div class=\"tree-node ").append(cellStatusClass(node.status()))
            .append("\"><span class=\"tree-node-name\">").append(HtmlEscaper.encode(node.node()))
            .append("</span>: ").append(valueDisplay);
        if (!node.children().isEmpty()) {
            body.append("<div class=\"tree-children\">");
            for (TreeParameterValue.TreeNode child : node.children()) {
                appendTreeNode(body, child);
            }
            body.append("</div>");
        }
        body.append("</div>");
    }

    /** Cell/tree-node status class — like {@link #paramStatusClass} but NotApplicable yields no class. */
    private static String cellStatusClass(VerificationStatus status) {
        return switch (status) {
            case SUCCESS -> "param-success";
            case FAILURE -> "param-failure";
            case EXCEPTION -> "param-exception";
            case NOT_PROVIDED -> "param-not-provided";
            default -> ""; // NOT_APPLICABLE → no class
        };
    }

    private static String paramStatusClass(VerificationStatus status) {
        return switch (status) {
            case SUCCESS -> "param-success";
            case FAILURE -> "param-failure";
            case EXCEPTION -> "param-exception";
            case NOT_PROVIDED -> "param-not-provided";
            default -> "param-na"; // NOT_APPLICABLE
        };
    }

    private static String stepStatusClass(ScenarioStep step) {
        return switch (step.status()) {
            case PASSED -> hasAnySkipped(step) ? "passed-skipped"
                : hasAnyBypassed(step) ? "passed-bypassed" : "passed";
            case FAILED -> "failed";
            case SKIPPED -> "skipped";
            case INCONCLUSIVE -> "bypassed";
        };
    }

    private static String stepStatusIcon(ExecutionStatus status) {
        return switch (status) {
            case PASSED -> "&#10003;";
            case FAILED -> "&#10005;";
            case SKIPPED -> "&#216;";
            case INCONCLUSIVE -> "&#8631;";
        };
    }

    private static String stepStatusTooltip(ScenarioStep step) {
        return switch (step.status()) {
            case PASSED -> hasAnySkipped(step)
                ? "Passed (with skipped sub-steps) — all assertions passed, but one or more sub-steps were skipped. Skipped steps did not execute and also prevented execution of subsequent steps"
                : hasAnyBypassed(step)
                ? "Passed (with bypassed sub-steps) — all assertions passed, but one or more sub-steps were bypassed (intentionally skipped over without preventing execution of subsequent steps)"
                : "Passed — all assertions in this step passed";
            case FAILED -> "Failed — this step threw an exception or an assertion failed";
            case SKIPPED -> "Skipped — this step did not execute because it was intentionally skipped, either at the scenario level, or at the step level. In the latter case the skip also prevented execution of subsequent steps";
            case INCONCLUSIVE -> "Bypassed — some or all of the logic in this step was intentionally skipped over without preventing execution of subsequent steps";
        };
    }

    private static boolean hasAnyFailed(ScenarioStep step) {
        for (ScenarioStep sub : step.subSteps()) {
            if (sub.status() == ExecutionStatus.FAILED || hasAnyFailed(sub)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyBypassed(ScenarioStep step) {
        for (ScenarioStep sub : step.subSteps()) {
            if (sub.status() == ExecutionStatus.INCONCLUSIVE || hasAnyBypassed(sub)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnySkipped(ScenarioStep step) {
        for (ScenarioStep sub : step.subSteps()) {
            if (sub.status() == ExecutionStatus.SKIPPED || hasAnySkipped(sub)) {
                return true;
            }
        }
        return false;
    }

    private static String scenarioTooltip(ExecutionStatus status) {
        return switch (status) {
            case PASSED -> "Passed — all assertions passed";
            case FAILED -> "Failed — an assertion or runtime failure occurred";
            case SKIPPED -> "Skipped — either the entire test did not run (e.g. a skip attribute or filter excluded it), or a step was skipped at runtime which also prevented all subsequent steps from executing";
            case INCONCLUSIVE -> "Bypassed — some or all of the logic in a step was intentionally skipped over at runtime without preventing execution of subsequent steps";
        };
    }

    // -------------------------------------------------------------------------------- helpers -----

    static String asset(String name) {
        return ASSET_CACHE.computeIfAbsent(name, n -> {
            String resource = "io/kronikol/report/assets/" + n;
            try (InputStream in =
                     DotNetHtmlReportRenderer.class.getClassLoader().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("embedded asset not found: " + resource);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static String compressToBase64(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /** Compact, {@code System.Text.Json}-style serialization of the {@code puml-data} map: the default
     *  encoder escapes {@code "}/{@code +}/{@code <}/{@code >}/{@code &}/{@code '} and non-ASCII to
     *  upper-case {@code \\uXXXX} (plan §6.4), so the only byte-difference from .NET is the gzip payload. */
    static String pumlDataJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            jsonString(e.getKey(), sb);
            sb.append(':');
            jsonString(e.getValue(), sb);
        }
        sb.append('}');
        return sb.toString();
    }

    private static void jsonString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '"', '+', '<', '>', '&', '\'', '`' -> sb.append(String.format("\\u%04X", (int) c));
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        sb.append(String.format("\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    static Set<String> extractDependencies(String codeBehind) {
        Set<String> deps = new LinkedHashSet<>();
        if (codeBehind == null || codeBehind.isEmpty()) {
            return deps;
        }
        for (String line : codeBehind.split("\n")) {
            Matcher m = DEPENDENCY_RE.matcher(line.trim());
            if (m.find()) {
                deps.add(m.group(1));
            }
        }
        return deps;
    }

    static Set<String> extractDiagramSearchTerms(String codeBehind) {
        Set<String> terms = new LinkedHashSet<>();
        if (codeBehind == null || codeBehind.isEmpty()) {
            return terms;
        }
        for (String line : codeBehind.split("\n")) {
            String trimmed = line.trim();
            Matcher pm = PARTICIPANT_RE.matcher(trimmed);
            if (pm.find()) {
                terms.add(pm.group(1));
            }
            Matcher um = URL_RE.matcher(trimmed);
            if (um.find()) {
                terms.add(um.group(1));
            }
        }
        return terms;
    }

    static String scenarioAnchorId(String displayName) {
        String slug = ANCHOR_NON_ALNUM.matcher(displayName.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = trimHyphens(slug);
        return "scenario-" + slug;
    }

    private static String trimHyphens(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && s.charAt(start) == '-') {
            start++;
        }
        while (end > start && s.charAt(end - 1) == '-') {
            end--;
        }
        return s.substring(start, end);
    }

    static String formatDurationBadge(double durationMs) {
        double totalMs = Math.abs(durationMs);
        double totalSeconds = totalMs / 1000.0;
        if (totalSeconds < 1) {
            return (int) totalMs + "ms";
        }
        double totalMinutes = totalSeconds / 60.0;
        if (totalMinutes < 1) {
            return f1(totalSeconds) + "s";
        }
        int minutes = (int) totalMinutes;
        int seconds = (int) (totalSeconds - minutes * 60L);
        return minutes + "m " + seconds + "s";
    }

    private static double[] durationPercentiles(List<Feature> features) {
        double[] durations = features.stream()
            .flatMap(f -> f.scenarios().stream())
            .filter(s -> s.durationMs() > 0)
            .mapToLong(Scenario::durationMs)
            .sorted()
            .asDoubleStream()
            .toArray();
        if (durations.length == 0) {
            return new double[] {0, 0, 0, 0};
        }
        return new double[] {
            durations[(int) (durations.length * 0.50)],
            durations[(int) (durations.length * 0.90)],
            durations[(int) (durations.length * 0.95)],
            durations[(int) (durations.length * 0.99)]
        };
    }

    private static String f0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    private static String f1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String f2(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    /** Mirrors .NET's image-attachment test (Path.GetExtension lower-cased against the image set). */
    private static boolean isImageAttachment(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
        return ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg")
            || ext.equals(".gif") || ext.equals(".webp");
    }
}
