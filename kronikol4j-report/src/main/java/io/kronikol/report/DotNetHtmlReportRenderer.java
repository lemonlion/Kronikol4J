package io.kronikol.report;

import io.kronikol.report.html.HtmlEscaper;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
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
        boolean hasComponent = componentDiagram != null && !componentDiagram.isEmpty();
        Map<String, String> diagramData = new LinkedHashMap<>();
        StringBuilder head = new StringBuilder(300_000);
        appendHead(head, title, version, hasComponent);

        StringBuilder body = new StringBuilder(8_192);
        appendBody(body, title, features, diagramByTestId == null ? Map.of() : diagramByTestId,
            hasComponent ? componentDiagram : null, diagramData);

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

    private static void appendBody(StringBuilder body, String title, List<Feature> features,
                                   Map<String, String> diagramByTestId, String componentDiagram,
                                   Map<String, String> diagramData) {
        body.append("<h1>").append(title).append("</h1>");

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

            // Group consecutive same-Rule scenarios under a <details class="rule"> (.NET rule grouping).
            String currentRule = "__NOTSET__";
            boolean ruleOpen = false;
            for (Scenario scenario : ordered) {
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

        if (!scenario.backgroundSteps().isEmpty()) {
            body.append("<details class=\"scenario-background\">");
            body.append("<summary class=\"h4\">Background Steps</summary>");
            for (ScenarioStep step : scenario.backgroundSteps()) {
                appendStep(body, step, null); // showStepNumbers defaults off → no number prefix
            }
            body.append("</details>");
        }
        if (!scenario.steps().isEmpty()) {
            body.append("<details class=\"scenario-steps\" open>");
            body.append("<summary class=\"h4\">Steps</summary>");
            for (ScenarioStep step : scenario.steps()) {
                appendStep(body, step, null);
            }
            body.append("</details>");
        }
        if (!scenario.attachments().isEmpty()) {
            body.append("<div class=\"scenario-attachments\">");
            for (FileAttachment att : scenario.attachments()) {
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
    private static void appendStep(StringBuilder body, ScenarioStep step, String numberPrefix) {
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
        body.append("<span class=\"step-text\">").append(HtmlEscaper.encode(step.text())).append("</span>");
        if (step.durationMs() != null) {
            body.append(" <span class=\"step-duration\">(").append(formatDurationBadge(step.durationMs())).append(")</span>");
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

        if (hasSub) {
            body.append("</summary>");
            body.append("<div class=\"sub-steps\">");
            for (int i = 0; i < step.subSteps().size(); i++) {
                String subPrefix = numberPrefix != null ? numberPrefix + (i + 1) + "." : null;
                appendStep(body, step.subSteps().get(i), subPrefix);
            }
            body.append("</div>");
            body.append("</details>");
        } else {
            body.append("</div>");
        }
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
