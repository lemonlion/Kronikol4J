package io.kronikol.report.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the JSON Schema describing the report-data {@code TestRunReport.json} — a faithful port of the
 * .NET {@code ReportGenerator.GenerateTestRunReportJsonSchema} (JSON Schema draft 2020-12). Used for the
 * {@code .schema.json} artifact (and for YAML, which reuses the JSON Schema). Serialized with the same
 * System.Text.Json {@code WriteIndented} format as the report-data JSON ({@link ReportDataSerializer}).
 *
 * <p>The {@code result}/{@code status} enums carry the .NET {@code ExecutionResult} names
 * ({@code Passed/Failed/Skipped/Bypassed/SkippedAfterFailure}) — the report-data wire contract — rather
 * than the Java {@code ExecutionStatus} names.
 */
public final class ReportDataSchema {

    private ReportDataSchema() {
    }

    /** The .NET {@code ExecutionResult} names, in declaration order (the report-data wire values). */
    private static final List<Object> RESULT_ENUM =
        arr("Passed", "Failed", "Skipped", "Bypassed", "SkippedAfterFailure");

    public static String jsonSchema() {
        Map<String, Object> attachmentItems = obj(
            "type", "object",
            "properties", obj(
                "name", obj("type", "string"),
                "relativePath", obj("type", "string")));

        Map<String, Object> stepDef = obj(
            "type", "object",
            "properties", obj(
                "keyword", obj("type", "string", "nullable", true),
                "text", obj("type", "string"),
                "status", obj("type", "string", "enum", RESULT_ENUM, "nullable", true),
                "durationSeconds", obj("type", "number", "nullable", true),
                "subSteps", obj("type", "array", "items", ref("step")),
                "attachments", obj("type", "array", "items", attachmentItems)));

        Map<String, Object> httpInteractionDef = obj(
            "type", "object",
            "properties", obj(
                "type", obj("type", "string", "enum", arr("Request", "Response")),
                "method", obj("type", "string", "nullable", true),
                "uri", obj("type", "string", "format", "uri"),
                "serviceName", obj("type", "string"),
                "callerName", obj("type", "string"),
                "content", obj("type", "string", "nullable", true),
                "headers", obj("type", "array", "items", obj(
                    "type", "object",
                    "properties", obj(
                        "key", obj("type", "string"),
                        "value", obj("type", "string", "nullable", true)))),
                "statusCode", obj("type", "string", "nullable", true),
                "traceId", obj("type", "string", "format", "uuid"),
                "requestResponseId", obj("type", "string", "format", "uuid"),
                "timestamp", obj("type", "string", "format", "date-time", "nullable", true)));

        Map<String, Object> scenarioItems = obj(
            "type", "object",
            "required", arr("id", "stableId", "name", "result", "durationSeconds", "isHappyPath",
                "labels", "categories", "steps"),
            "properties", obj(
                "id", obj("type", "string"),
                "stableId", obj("type", "string", "description", "Deterministic cross-run identifier "
                    + "derived from feature name + scenario display name (+ outline ID for parameterized "
                    + "scenarios). Use this for matching the same test across runs."),
                "name", obj("type", "string"),
                "result", obj("type", "string", "enum", RESULT_ENUM),
                "durationSeconds", obj("type", "number"),
                "isHappyPath", obj("type", "boolean"),
                "errorMessage", obj("type", "string", "nullable", true),
                "errorStackTrace", obj("type", "string", "nullable", true),
                "labels", obj("type", "array", "items", obj("type", "string")),
                "categories", obj("type", "array", "items", obj("type", "string")),
                "rule", obj("type", "string", "nullable", true, "description",
                    "Gherkin Rule grouping this scenario belongs to"),
                "outlineId", obj("type", "string", "nullable", true, "description",
                    "Original scenario outline name for parameterized scenarios"),
                "exampleValues", obj("type", "object", "nullable", true, "description",
                    "Example parameter values for parameterized scenarios",
                    "additionalProperties", obj("type", "string")),
                "backgroundSteps", obj("type", "array", "items", ref("step")),
                "steps", obj("type", "array", "items", ref("step")),
                "attachments", obj("type", "array", "description",
                    "Scenario-level file attachments (added when no step was active)",
                    "items", attachmentItems),
                "diagrams", obj("type", "array", "items", obj("type", "string")),
                "httpInteractions", obj("type", "array", "items", ref("httpInteraction"))));

        Map<String, Object> schema = obj(
            "$schema", "https://json-schema.org/draft/2020-12/schema",
            "title", "TestRunReport",
            "description", "Schema for Kronikol test run report data",
            "type", "object",
            "required", arr("startTime", "endTime", "features"),
            "properties", obj(
                "kronikolVersion", obj("type", "string", "description",
                    "Version of Kronikol that generated this report"),
                "startTime", obj("type", "string", "format", "date-time", "description",
                    "UTC start time of the test run"),
                "endTime", obj("type", "string", "format", "date-time", "description",
                    "UTC end time of the test run"),
                "features", obj("type", "array", "items", obj(
                    "type", "object",
                    "required", arr("name", "labels", "scenarios"),
                    "properties", obj(
                        "name", obj("type", "string"),
                        "endpoint", obj("type", "string", "nullable", true),
                        "description", obj("type", "string", "nullable", true),
                        "labels", obj("type", "array", "items", obj("type", "string")),
                        "scenarios", obj("type", "array", "items", scenarioItems))))),
            "$defs", obj(
                "step", stepDef,
                "httpInteraction", httpInteractionDef));

        return ReportDataSerializer.toIndentedJson(schema);
    }

    /** The XSD describing the report-data XML (.NET {@code GenerateTestRunReportXmlSchema}), serialized
     *  in the {@code XDocument.ToString} format (2-space indent, self-closing empty elements, no XML
     *  declaration) — the same convention {@link ReportDataSerializer#toXml} matches. */
    public static String xmlSchema() {
        Xml restriction = el("xs:restriction").a("base", "xs:string");
        for (Object v : RESULT_ENUM) {
            restriction.c(el("xs:enumeration").a("value", (String) v));
        }
        Xml executionResultType = el("xs:simpleType").a("name", "ExecutionResult").c(restriction);

        Xml headerType = el("xs:complexType").a("name", "HeaderType").c(el("xs:sequence").c(
            el("xs:element").a("name", "Key").a("type", "xs:string"),
            el("xs:element").a("name", "Value").a("type", "xs:string").a("minOccurs", "0")));

        Xml stepType = el("xs:complexType").a("name", "StepType").c(el("xs:sequence").c(
            el("xs:element").a("name", "Keyword").a("type", "xs:string").a("minOccurs", "0"),
            el("xs:element").a("name", "Text").a("type", "xs:string"),
            el("xs:element").a("name", "Status").a("type", "ExecutionResult").a("minOccurs", "0"),
            el("xs:element").a("name", "DurationSeconds").a("type", "xs:decimal").a("minOccurs", "0"),
            listWrapper("SubSteps", "Step", "StepType", true),
            attachmentsWrapper()));

        Xml httpInteractionType = el("xs:complexType").a("name", "HttpInteractionType").c(el("xs:sequence").c(
            el("xs:element").a("name", "Type").a("type", "xs:string"),
            el("xs:element").a("name", "Method").a("type", "xs:string").a("minOccurs", "0"),
            el("xs:element").a("name", "Uri").a("type", "xs:string"),
            el("xs:element").a("name", "ServiceName").a("type", "xs:string"),
            el("xs:element").a("name", "CallerName").a("type", "xs:string"),
            el("xs:element").a("name", "Content").a("type", "xs:string").a("minOccurs", "0"),
            listWrapper("Headers", "Header", "HeaderType", true),
            el("xs:element").a("name", "StatusCode").a("type", "xs:string").a("minOccurs", "0"),
            el("xs:element").a("name", "TraceId").a("type", "xs:string"),
            el("xs:element").a("name", "RequestResponseId").a("type", "xs:string"),
            el("xs:element").a("name", "Timestamp").a("type", "xs:string").a("minOccurs", "0")));

        Xml scenarioType = el("xs:complexType").a("name", "ScenarioType").c(el("xs:sequence").c(
            el("xs:element").a("name", "Id").a("type", "xs:string"),
            el("xs:element").a("name", "StableId").a("type", "xs:string"),
            el("xs:element").a("name", "Name").a("type", "xs:string"),
            el("xs:element").a("name", "Result").a("type", "ExecutionResult"),
            el("xs:element").a("name", "DurationSeconds").a("type", "xs:decimal"),
            el("xs:element").a("name", "IsHappyPath").a("type", "xs:boolean"),
            el("xs:element").a("name", "ErrorMessage").a("type", "xs:string").a("minOccurs", "0"),
            el("xs:element").a("name", "ErrorStackTrace").a("type", "xs:string").a("minOccurs", "0"),
            listWrapper("Labels", "Label", "xs:string", true),
            listWrapper("Categories", "Category", "xs:string", true),
            el("xs:element").a("name", "Rule").a("type", "xs:string").a("minOccurs", "0"),
            listWrapper("BackgroundSteps", "Step", "StepType", true),
            listWrapper("Steps", "Step", "StepType", true),
            attachmentsWrapper(),
            listWrapper("Diagrams", "Diagram", "xs:string", true),
            listWrapper("HttpInteractions", "HttpInteraction", "HttpInteractionType", true)));

        Xml featureType = el("xs:complexType").a("name", "FeatureType").c(el("xs:sequence").c(
            el("xs:element").a("name", "Name").a("type", "xs:string"),
            el("xs:element").a("name", "Endpoint").a("type", "xs:string").a("minOccurs", "0"),
            el("xs:element").a("name", "Description").a("type", "xs:string").a("minOccurs", "0"),
            listWrapper("Labels", "Label", "xs:string", true),
            listWrapper("Scenarios", "Scenario", "ScenarioType", false)));

        Xml root = el("xs:schema").a("xmlns:xs", "http://www.w3.org/2001/XMLSchema").c(
            executionResultType, headerType, stepType, httpInteractionType, scenarioType, featureType,
            el("xs:element").a("name", "TestRunReport").c(el("xs:complexType").c(el("xs:sequence").c(
                el("xs:element").a("name", "KronikolVersion").a("type", "xs:string").a("minOccurs", "0"),
                el("xs:element").a("name", "StartTime").a("type", "xs:string"),
                el("xs:element").a("name", "EndTime").a("type", "xs:string"),
                listWrapper("Features", "Feature", "FeatureType", false)))));

        StringBuilder sb = new StringBuilder();
        writeXml(sb, root, "");
        return sb.toString();
    }

    /** The repeated {@code <Wrapper><complexType><sequence><Item .../></sequence>…} schema shape. */
    private static Xml listWrapper(String wrapper, String item, String type, boolean optional) {
        Xml w = el("xs:element").a("name", wrapper);
        if (optional) {
            w.a("minOccurs", "0");
        }
        return w.c(el("xs:complexType").c(el("xs:sequence").c(
            el("xs:element").a("name", item).a("type", type).a("minOccurs", "0").a("maxOccurs", "unbounded"))));
    }

    private static Xml attachmentsWrapper() {
        return el("xs:element").a("name", "Attachments").a("minOccurs", "0").c(
            el("xs:complexType").c(el("xs:sequence").c(
                el("xs:element").a("name", "Attachment").a("minOccurs", "0").a("maxOccurs", "unbounded").c(
                    el("xs:complexType").c(el("xs:sequence").c(
                        el("xs:element").a("name", "Name").a("type", "xs:string"),
                        el("xs:element").a("name", "RelativePath").a("type", "xs:string")))))));
    }

    /** Serializes an {@link Xml} tree like .NET {@code XElement.ToString}: 2-space indent, attributes in
     *  insertion order, empty elements self-closed as {@code <tag … />}, no XML declaration. */
    private static void writeXml(StringBuilder sb, Xml n, String indent) {
        sb.append(indent).append('<').append(n.tag);
        for (Map.Entry<String, String> a : n.attrs.entrySet()) {
            sb.append(' ').append(a.getKey()).append("=\"").append(xmlEscape(a.getValue())).append('"');
        }
        if (n.children.isEmpty()) {
            sb.append(" />");
        } else {
            sb.append('>');
            for (Xml child : n.children) {
                sb.append('\n');
                writeXml(sb, child, indent + "  ");
            }
            sb.append('\n').append(indent).append("</").append(n.tag).append('>');
        }
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Xml el(String tag) {
        return new Xml(tag);
    }

    /** A minimal XML element node (tag + ordered attributes + children) for building the XSD. */
    private static final class Xml {
        final String tag;
        final Map<String, String> attrs = new LinkedHashMap<>();
        final List<Xml> children = new ArrayList<>();

        Xml(String tag) {
            this.tag = tag;
        }

        Xml a(String key, String value) {
            attrs.put(key, value);
            return this;
        }

        Xml c(Xml... cs) {
            children.addAll(Arrays.asList(cs));
            return this;
        }
    }

    private static Map<String, Object> obj(Object... kv) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static List<Object> arr(Object... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static Map<String, Object> ref(String def) {
        return obj("$ref", "#/$defs/" + def);
    }
}
