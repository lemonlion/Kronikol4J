package io.kronikol.report.data;

import io.kronikol.core.tracking.Header;
import io.kronikol.core.tracking.RequestResponseLog;
import io.kronikol.core.tracking.RequestResponseType;
import io.kronikol.core.tracking.StatusCode;
import io.kronikol.diagram.plantuml.HttpStatusNames;
import io.kronikol.report.model.ExecutionStatus;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.FileAttachment;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Serializes the {@link ReportData} test-run report to JSON, XML, and YAML — aligned
 * <strong>byte-for-byte</strong> with the .NET {@code GenerateTestRunReportData} (verified by the
 * golden-file parity suite). Each format reproduces .NET's exact field set, ordering, escaping,
 * number formatting, and conditional inclusion (which differ between the three).
 */
public final class ReportDataSerializer {

    private static final String NL = "\n";
    private static final DateTimeFormatter RUN_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(java.time.ZoneOffset.UTC);
    private static final DateTimeFormatter LOG_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private ReportDataSerializer() {
    }

    private static List<Feature> featuresOrdered(ReportData data) {
        List<Feature> fs = new ArrayList<>(data.features());
        fs.sort(Comparator.comparing(Feature::displayName)); // .NET OrderBy(f => f.DisplayName)
        return fs;
    }

    // ---------------------------------------------------------------- JSON (System.Text.Json indent)

    public static String toJson(ReportData data) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("kronikolVersion", data.kronikolVersion());
        root.put("startTime", RUN_TIME.format(data.startTime()));
        root.put("endTime", RUN_TIME.format(data.endTime()));
        List<Object> features = new ArrayList<>();
        for (Feature f : featuresOrdered(data)) {
            Map<String, Object> fo = new LinkedHashMap<>();
            fo.put("name", f.displayName());
            fo.put("endpoint", f.endpoint());
            fo.put("description", f.description());
            fo.put("labels", new ArrayList<Object>(f.labels()));
            List<Object> scenarios = new ArrayList<>();
            for (Scenario s : f.scenarios()) {
                scenarios.add(scenarioJson(f, s, data));
            }
            fo.put("scenarios", scenarios);
            features.add(fo);
        }
        root.put("features", features);
        StringBuilder sb = new StringBuilder(2048);
        writeJson(sb, root, "");
        return sb.toString();
    }

    private static Map<String, Object> scenarioJson(Feature f, Scenario s, ReportData data) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("id", s.testId());
        o.put("stableId", s.stableId(f.displayName()));
        o.put("name", s.name());
        o.put("result", s.status().displayName());
        o.put("durationSeconds", s.durationMs() / 1000.0);
        o.put("isHappyPath", s.isHappyPath());
        o.put("errorMessage", s.error());
        o.put("errorStackTrace", s.errorStackTrace());
        o.put("labels", new ArrayList<Object>(s.labels()));
        o.put("categories", new ArrayList<Object>(s.categories()));
        o.put("rule", s.rule());
        o.put("outlineId", s.outlineId());
        o.put("exampleValues", s.exampleValues() == null ? null : new LinkedHashMap<Object, Object>(s.exampleValues()));
        o.put("exampleDisplayName", s.exampleDisplayName());
        o.put("attachments", attachmentsJson(s.attachments()));
        o.put("backgroundSteps", stepsJson(s.backgroundSteps()));
        o.put("steps", stepsJson(s.steps()));
        if (data.diagramsByTestId() != null) {
            o.put("diagrams", new ArrayList<Object>(diagramsFor(data, s.testId())));
        }
        if (data.logsByTestId() != null) {
            List<Object> logs = new ArrayList<>();
            for (RequestResponseLog log : logsFor(data, s.testId())) {
                logs.add(logJson(log));
            }
            o.put("httpInteractions", logs);
        }
        return o;
    }

    private static List<Object> stepsJson(List<ScenarioStep> steps) {
        List<Object> out = new ArrayList<>();
        for (ScenarioStep step : steps) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("keyword", step.keyword());
            o.put("text", step.text());
            o.put("status", step.status() == null ? null : step.status().displayName());
            o.put("durationSeconds", step.durationMs() == null ? null : step.durationMs() / 1000.0);
            o.put("subSteps", stepsJson(step.subSteps()));
            o.put("attachments", attachmentsJson(step.attachments()));
            out.add(o);
        }
        return out;
    }

    private static List<Object> attachmentsJson(List<FileAttachment> attachments) {
        List<Object> out = new ArrayList<>();
        for (FileAttachment a : attachments) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", a.name());
            o.put("relativePath", a.relativePath());
            out.add(o);
        }
        return out;
    }

    private static Map<String, Object> logJson(RequestResponseLog log) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("type", logType(log.type()));
        o.put("method", logMethod(log));
        o.put("uri", log.uri().toString());
        o.put("serviceName", log.serviceName());
        o.put("callerName", log.callerName());
        o.put("content", log.content());
        List<Object> headers = new ArrayList<>();
        for (Header h : log.headers()) {
            Map<String, Object> ho = new LinkedHashMap<>();
            ho.put("key", h.key());
            ho.put("value", h.value());
            headers.add(ho);
        }
        o.put("headers", headers);
        o.put("statusCode", statusCode(log.statusCode()));
        o.put("traceId", log.traceId().toString());
        o.put("requestResponseId", log.requestResponseId().toString());
        o.put("timestamp", log.timestamp() == null ? null : LOG_TIME.format(log.timestamp()));
        return o;
    }

    @SuppressWarnings("unchecked")
    /** Serializes an arbitrary Map/List/String/Boolean/Double tree with the System.Text.Json
     *  {@code WriteIndented} format (shared by the report-data JSON and {@link ReportDataSchema}). */
    static String toIndentedJson(Object tree) {
        StringBuilder sb = new StringBuilder();
        writeJson(sb, tree, "");
        return sb.toString();
    }

    private static void writeJson(StringBuilder sb, Object value, String indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"');
            escapeJson(sb, s);
            sb.append('"');
        } else if (value instanceof Boolean b) {
            sb.append(b ? "true" : "false");
        } else if (value instanceof Double d) {
            sb.append(jsonNumber(d));
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{").append(NL);
            String inner = indent + "  ";
            int i = 0;
            for (Map.Entry<?, ?> e : ((Map<Object, Object>) map).entrySet()) {
                sb.append(inner).append('"');
                escapeJson(sb, String.valueOf(e.getKey()));
                sb.append("\": ");
                writeJson(sb, e.getValue(), inner);
                if (++i < map.size()) {
                    sb.append(',');
                }
                sb.append(NL);
            }
            sb.append(indent).append("}");
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[").append(NL);
            String inner = indent + "  ";
            for (int i = 0; i < list.size(); i++) {
                sb.append(inner);
                writeJson(sb, list.get(i), inner);
                if (i + 1 < list.size()) {
                    sb.append(',');
                }
                sb.append(NL);
            }
            sb.append(indent).append("]");
        } else {
            throw new IllegalArgumentException("unsupported JSON value: " + value.getClass());
        }
    }

    /** System.Text.Json default-encoder escaping: {@code "&<>'+`} and control + non-ASCII → {@code \\uXXXX}. */
    private static void escapeJson(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7E
                        || c == '"' || c == '&' || c == '<' || c == '>' || c == '\'' || c == '+' || c == '`') {
                        sb.append(String.format("\\u%04X", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
    }

    private static String jsonNumber(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            return Long.toString((long) d); // whole → "0", "2" (matches System.Text.Json)
        }
        return Double.toString(d);
    }

    // ---------------------------------------------------------------- XML (XDocument.ToString)

    public static String toXml(ReportData data) {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("<TestRunReport>").append(NL);
        xmlLeaf(sb, "  ", "KronikolVersion", data.kronikolVersion());
        xmlLeaf(sb, "  ", "StartTime", RUN_TIME.format(data.startTime()));
        xmlLeaf(sb, "  ", "EndTime", RUN_TIME.format(data.endTime()));
        sb.append("  <Features>").append(NL);
        for (Feature f : featuresOrdered(data)) {
            sb.append("    <Feature>").append(NL);
            xmlLeaf(sb, "      ", "Name", f.displayName());
            if (f.endpoint() != null) {
                xmlLeaf(sb, "      ", "Endpoint", f.endpoint());
            }
            if (f.description() != null) {
                xmlLeaf(sb, "      ", "Description", f.description());
            }
            xmlList(sb, "      ", "Labels", "Label", f.labels());
            sb.append("      <Scenarios>").append(NL);
            for (Scenario s : f.scenarios()) {
                xmlScenario(sb, f, s, data);
            }
            sb.append("      </Scenarios>").append(NL);
            sb.append("    </Feature>").append(NL);
        }
        sb.append("  </Features>").append(NL);
        sb.append("</TestRunReport>");
        return sb.toString();
    }

    private static void xmlScenario(StringBuilder sb, Feature f, Scenario s, ReportData data) {
        sb.append("        <Scenario>").append(NL);
        xmlLeaf(sb, "          ", "Id", s.testId());
        xmlLeaf(sb, "          ", "StableId", s.stableId(f.displayName()));
        xmlLeaf(sb, "          ", "Name", s.name());
        xmlLeaf(sb, "          ", "Result", s.status().displayName());
        xmlLeaf(sb, "          ", "DurationSeconds", f3(s.durationMs() / 1000.0));
        xmlLeaf(sb, "          ", "IsHappyPath", Boolean.toString(s.isHappyPath()));
        if (s.error() != null) {
            xmlLeaf(sb, "          ", "ErrorMessage", s.error());
        }
        if (s.errorStackTrace() != null) {
            xmlLeaf(sb, "          ", "ErrorStackTrace", s.errorStackTrace());
        }
        xmlList(sb, "          ", "Labels", "Label", s.labels());
        xmlList(sb, "          ", "Categories", "Category", s.categories());
        if (s.rule() != null) {
            xmlLeaf(sb, "          ", "Rule", s.rule());
        }
        if (!s.backgroundSteps().isEmpty()) {
            sb.append("          <BackgroundSteps>").append(NL);
            for (ScenarioStep step : s.backgroundSteps()) {
                xmlStep(sb, "            ", step);
            }
            sb.append("          </BackgroundSteps>").append(NL);
        }
        if (!s.steps().isEmpty()) {
            sb.append("          <Steps>").append(NL);
            for (ScenarioStep step : s.steps()) {
                xmlStep(sb, "            ", step);
            }
            sb.append("          </Steps>").append(NL);
        }
        if (!s.attachments().isEmpty()) {
            xmlAttachments(sb, "          ", s.attachments());
        }
        if (data.diagramsByTestId() != null) {
            List<String> diags = diagramsFor(data, s.testId());
            if (!diags.isEmpty()) {
                sb.append("          <Diagrams>").append(NL);
                for (String d : diags) {
                    xmlLeaf(sb, "            ", "Diagram", d);
                }
                sb.append("          </Diagrams>").append(NL);
            }
        }
        if (data.logsByTestId() != null) {
            List<RequestResponseLog> logs = logsFor(data, s.testId());
            if (!logs.isEmpty()) {
                sb.append("          <HttpInteractions>").append(NL);
                for (RequestResponseLog log : logs) {
                    xmlLog(sb, "            ", log);
                }
                sb.append("          </HttpInteractions>").append(NL);
            }
        }
        sb.append("        </Scenario>").append(NL);
    }

    private static void xmlStep(StringBuilder sb, String indent, ScenarioStep step) {
        sb.append(indent).append("<Step>").append(NL);
        String in = indent + "  ";
        if (step.keyword() != null) {
            xmlLeaf(sb, in, "Keyword", step.keyword());
        }
        xmlLeaf(sb, in, "Text", step.text());
        if (step.status() != null) {
            xmlLeaf(sb, in, "Status", step.status().displayName());
        }
        if (step.durationMs() != null) {
            xmlLeaf(sb, in, "DurationSeconds", f3(step.durationMs() / 1000.0));
        }
        if (!step.subSteps().isEmpty()) {
            sb.append(in).append("<SubSteps>").append(NL);
            for (ScenarioStep sub : step.subSteps()) {
                xmlStep(sb, in + "  ", sub);
            }
            sb.append(in).append("</SubSteps>").append(NL);
        }
        if (!step.attachments().isEmpty()) {
            xmlAttachments(sb, in, step.attachments());
        }
        sb.append(indent).append("</Step>").append(NL);
    }

    private static void xmlAttachments(StringBuilder sb, String indent, List<FileAttachment> attachments) {
        sb.append(indent).append("<Attachments>").append(NL);
        for (FileAttachment a : attachments) {
            sb.append(indent).append("  <Attachment>").append(NL);
            xmlLeaf(sb, indent + "    ", "Name", a.name());
            xmlLeaf(sb, indent + "    ", "RelativePath", a.relativePath());
            sb.append(indent).append("  </Attachment>").append(NL);
        }
        sb.append(indent).append("</Attachments>").append(NL);
    }

    private static void xmlLog(StringBuilder sb, String indent, RequestResponseLog log) {
        sb.append(indent).append("<HttpInteraction>").append(NL);
        String in = indent + "  ";
        xmlLeaf(sb, in, "Type", logType(log.type()));
        xmlLeaf(sb, in, "Method", logMethod(log));
        xmlLeaf(sb, in, "Uri", log.uri().toString());
        xmlLeaf(sb, in, "ServiceName", log.serviceName());
        xmlLeaf(sb, in, "CallerName", log.callerName());
        if (log.content() != null) {
            xmlLeaf(sb, in, "Content", log.content());
        }
        if (!log.headers().isEmpty()) {
            sb.append(in).append("<Headers>").append(NL);
            for (Header h : log.headers()) {
                sb.append(in).append("  <Header>").append(NL);
                xmlLeaf(sb, in + "    ", "Key", h.key());
                xmlLeaf(sb, in + "    ", "Value", h.value());
                sb.append(in).append("  </Header>").append(NL);
            }
            sb.append(in).append("</Headers>").append(NL);
        }
        String status = statusCode(log.statusCode());
        if (status != null) {
            xmlLeaf(sb, in, "StatusCode", status);
        }
        xmlLeaf(sb, in, "TraceId", log.traceId().toString());
        xmlLeaf(sb, in, "RequestResponseId", log.requestResponseId().toString());
        if (log.timestamp() != null) {
            xmlLeaf(sb, in, "Timestamp", LOG_TIME.format(log.timestamp()));
        }
        sb.append(indent).append("</HttpInteraction>").append(NL);
    }

    private static void xmlList(StringBuilder sb, String indent, String wrapper, String item, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        sb.append(indent).append('<').append(wrapper).append('>').append(NL);
        for (String v : values) {
            xmlLeaf(sb, indent + "  ", item, v);
        }
        sb.append(indent).append("</").append(wrapper).append('>').append(NL);
    }

    private static void xmlLeaf(StringBuilder sb, String indent, String tag, String value) {
        sb.append(indent).append('<').append(tag).append('>');
        xmlText(sb, value == null ? "" : value);
        sb.append("</").append(tag).append('>').append(NL);
    }

    private static void xmlText(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
    }

    // ---------------------------------------------------------------- YAML (hand-built, SanitiseForYml)

    public static String toYaml(ReportData data) {
        StringBuilder y = new StringBuilder(2048);
        y.append("KronikolVersion: ").append(data.kronikolVersion()).append(NL);
        y.append("StartTime: ").append(RUN_TIME.format(data.startTime())).append(NL);
        y.append("EndTime: ").append(RUN_TIME.format(data.endTime())).append(NL);
        y.append("Features:").append(NL);
        for (Feature f : featuresOrdered(data)) {
            y.append("  - Name: ").append(yml(f.displayName())).append(NL);
            if (f.endpoint() != null) {
                y.append("    Endpoint: ").append(f.endpoint()).append(NL);
            }
            if (f.description() != null) {
                y.append("    Description: ").append(yml(f.description())).append(NL);
            }
            ymlList(y, "    ", "Labels", f.labels());
            y.append("    Scenarios:").append(NL);
            for (Scenario s : f.scenarios()) {
                ymlScenario(y, f, s, data);
            }
        }
        return y.toString();
    }

    private static void ymlScenario(StringBuilder y, Feature f, Scenario s, ReportData data) {
        y.append("      - Name: ").append(yml(s.name())).append(NL);
        y.append("        StableId: ").append(s.stableId(f.displayName())).append(NL);
        y.append("        Result: ").append(s.status().displayName()).append(NL);
        y.append("        DurationSeconds: ").append(f3(s.durationMs() / 1000.0)).append(NL);
        y.append("        IsHappyPath: ").append(Boolean.toString(s.isHappyPath())).append(NL);
        if (s.error() != null) {
            y.append("        ErrorMessage: ").append(yml(s.error())).append(NL);
        }
        if (s.errorStackTrace() != null) {
            y.append("        ErrorStackTrace: ").append(yml(s.errorStackTrace())).append(NL);
        }
        ymlList(y, "        ", "Labels", s.labels());
        ymlList(y, "        ", "Categories", s.categories());
        if (s.rule() != null) {
            y.append("        Rule: ").append(yml(s.rule())).append(NL);
        }
        if (!s.backgroundSteps().isEmpty()) {
            y.append("        BackgroundSteps:").append(NL);
            for (ScenarioStep step : s.backgroundSteps()) {
                ymlStep(y, step, "          ");
            }
        }
        if (!s.steps().isEmpty()) {
            y.append("        Steps:").append(NL);
            for (ScenarioStep step : s.steps()) {
                ymlStep(y, step, "          ");
            }
        }
        if (!s.attachments().isEmpty()) {
            y.append("        Attachments:").append(NL);
            for (FileAttachment a : s.attachments()) {
                y.append("          - Name: ").append(yml(a.name())).append(NL);
                y.append("            RelativePath: ").append(yml(a.relativePath())).append(NL);
            }
        }
        if (data.diagramsByTestId() != null) {
            List<String> diags = diagramsFor(data, s.testId());
            if (!diags.isEmpty()) {
                y.append("        Diagrams:").append(NL);
                for (String d : diags) {
                    y.append("          - |").append(NL);
                    for (String line : d.split("\n", -1)) {
                        y.append("            ").append(line).append(NL);
                    }
                }
            }
        }
        if (data.logsByTestId() != null) {
            List<RequestResponseLog> logs = logsFor(data, s.testId());
            if (!logs.isEmpty()) {
                y.append("        HttpInteractions:").append(NL);
                for (RequestResponseLog log : logs) {
                    ymlLog(y, log, "          ");
                }
            }
        }
    }

    private static void ymlStep(StringBuilder y, ScenarioStep step, String indent) {
        y.append(indent).append("- Keyword: ").append(yml(step.keyword() == null ? "" : step.keyword())).append(NL);
        y.append(indent).append("  Text: ").append(yml(step.text())).append(NL);
        y.append(indent).append("  Status: ").append(step.status() == null ? "" : step.status().displayName()).append(NL);
        if (step.durationMs() != null) {
            y.append(indent).append("  DurationSeconds: ").append(f3(step.durationMs() / 1000.0)).append(NL);
        }
        if (!step.subSteps().isEmpty()) {
            y.append(indent).append("  SubSteps:").append(NL);
            for (ScenarioStep sub : step.subSteps()) {
                ymlStep(y, sub, indent + "    ");
            }
        }
        if (!step.attachments().isEmpty()) {
            y.append(indent).append("  Attachments:").append(NL);
            for (FileAttachment a : step.attachments()) {
                y.append(indent).append("    - Name: ").append(yml(a.name())).append(NL);
                y.append(indent).append("      RelativePath: ").append(yml(a.relativePath())).append(NL);
            }
        }
    }

    private static void ymlLog(StringBuilder y, RequestResponseLog log, String indent) {
        y.append(indent).append("- Type: ").append(logType(log.type())).append(NL);
        y.append(indent).append("  Method: ").append(logMethod(log)).append(NL);
        y.append(indent).append("  Uri: ").append(log.uri().toString()).append(NL);
        y.append(indent).append("  ServiceName: ").append(yml(log.serviceName())).append(NL);
        y.append(indent).append("  CallerName: ").append(yml(log.callerName())).append(NL);
        if (log.content() != null) {
            y.append(indent).append("  Content: ").append(yml(log.content())).append(NL);
        }
        String status = statusCode(log.statusCode());
        if (status != null) {
            y.append(indent).append("  StatusCode: ").append(status).append(NL);
        }
        y.append(indent).append("  TraceId: ").append(log.traceId().toString()).append(NL);
        y.append(indent).append("  RequestResponseId: ").append(log.requestResponseId().toString()).append(NL);
        if (log.timestamp() != null) {
            y.append(indent).append("  Timestamp: ").append(LOG_TIME.format(log.timestamp())).append(NL);
        }
        if (!log.headers().isEmpty()) {
            y.append(indent).append("  Headers:").append(NL);
            for (Header h : log.headers()) {
                y.append(indent).append("    - Key: ").append(yml(h.key())).append(NL);
                y.append(indent).append("      Value: ").append(yml(h.value() == null ? "" : h.value())).append(NL);
            }
        }
    }

    private static void ymlList(StringBuilder y, String indent, String key, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        y.append(indent).append(key).append(':').append(NL);
        for (String v : values) {
            y.append(indent).append("  - ").append(yml(v)).append(NL);
        }
    }

    /** The .NET {@code SanitiseForYml} character replacements. */
    private static String yml(String value) {
        return value
            .replace("[", "<").replace("]", ">")
            .replace(": ", " = ")
            .replace("#", "(hash)").replace("&", "(and)").replace("*", "(star)")
            .replace("{", "(").replace("}", ")")
            .replace("!", "(bang)").replace("%", "(pct)").replace("@", "(at)")
            .replace("`", "'").replace("|", "(pipe)");
    }

    // ---------------------------------------------------------------- shared scalar helpers

    private static String f3(double seconds) {
        return String.format(Locale.ROOT, "%.3f", seconds);
    }

    private static String logType(RequestResponseType type) {
        return type == RequestResponseType.REQUEST ? "Request" : "Response";
    }

    private static String logMethod(RequestResponseLog log) {
        return log.method() == null ? "" : log.method().value().toUpperCase(Locale.ROOT);
    }

    private static String statusCode(StatusCode status) {
        if (status instanceof StatusCode.Http http) {
            return HttpStatusNames.enumName(http.code());
        }
        if (status instanceof StatusCode.Custom custom) {
            return custom.value();
        }
        return null;
    }

    private static List<String> diagramsFor(ReportData data, String testId) {
        return data.diagramsByTestId().getOrDefault(testId, List.of());
    }

    private static List<RequestResponseLog> logsFor(ReportData data, String testId) {
        return data.logsByTestId().getOrDefault(testId, List.of());
    }
}
