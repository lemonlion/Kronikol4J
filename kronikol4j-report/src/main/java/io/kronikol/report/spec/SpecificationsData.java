package io.kronikol.report.spec;

import io.kronikol.report.data.ReportDataFormat;
import io.kronikol.report.html.HtmlEscaper;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.Scenario;
import io.kronikol.report.model.ScenarioStep;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Generates the Specifications data file (living documentation) in YAML / JSON / XML — a port of the .NET
 * {@code ReportGenerator.GenerateSpecifications{Yaml,Json,Xml}}. Features are ordered by display name and
 * scenarios by happy-path-first then name; steps are text-only ({@code "<keyword> <text>"}, sub-steps nested).
 * Distinct from the execution-focused {@code TestRunReport} data — this is the spec view.
 */
public final class SpecificationsData {

    private SpecificationsData() {
    }

    /** Generates the specifications data in {@code format}. */
    public static String generate(List<Feature> features, String title, ReportDataFormat format) {
        return switch (format) {
            case YAML -> yaml(features, title);
            case JSON -> json(features, title);
            case XML -> xml(features, title);
        };
    }

    private static List<Feature> orderedFeatures(List<Feature> features) {
        List<Feature> out = new ArrayList<>(features);
        out.sort(Comparator.comparing(Feature::displayName));
        return out;
    }

    private static List<Scenario> orderedScenarios(Feature feature) {
        List<Scenario> out = new ArrayList<>(feature.scenarios());
        out.sort(Comparator.comparing(Scenario::isHappyPath).reversed().thenComparing(Scenario::name));
        return out;
    }

    private static String stepText(ScenarioStep step) {
        return step.keyword() != null ? step.keyword() + " " + step.text() : step.text();
    }

    // ---------------------------------------------------------------- YAML

    public static String yaml(List<Feature> features, String title) {
        StringBuilder yml = new StringBuilder();
        yml.append("Title: ").append(title).append('\n');
        yml.append("Features:\n");
        for (Feature feature : orderedFeatures(features)) {
            yml.append("  - Feature: ").append(yml(feature.displayName())).append('\n');
            if (feature.endpoint() != null) {
                yml.append("    Endpoint: ").append(feature.endpoint()).append('\n');
            }
            if (feature.description() != null) {
                yml.append("    Description: ").append(yml(feature.description())).append('\n');
            }
            if (!feature.labels().isEmpty()) {
                yml.append("    Labels:\n");
                for (String label : feature.labels()) {
                    yml.append("      - ").append(yml(label)).append('\n');
                }
            }
            yml.append("    Scenarios:\n");
            for (Scenario scenario : orderedScenarios(feature)) {
                yml.append("      - Scenario: ").append(yml(scenario.name())).append('\n');
                yml.append("        IsHappyPath: ").append(scenario.isHappyPath()).append('\n');
                if (!scenario.labels().isEmpty()) {
                    yml.append("        Labels:\n");
                    for (String label : scenario.labels()) {
                        yml.append("          - ").append(yml(label)).append('\n');
                    }
                }
                if (!scenario.categories().isEmpty()) {
                    yml.append("        Categories:\n");
                    for (String cat : scenario.categories()) {
                        yml.append("          - ").append(yml(cat)).append('\n');
                    }
                }
                if (!scenario.steps().isEmpty()) {
                    yml.append("        Steps:\n");
                    for (ScenarioStep step : scenario.steps()) {
                        appendYamlStep(yml, step, "          ");
                    }
                }
                yml.append('\n');
            }
        }
        return yml.toString();
    }

    private static void appendYamlStep(StringBuilder yml, ScenarioStep step, String indent) {
        yml.append(indent).append("- ").append(yml(stepText(step))).append('\n');
        for (ScenarioStep sub : step.subSteps()) {
            appendYamlStep(yml, sub, indent + "  ");
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

    // ---------------------------------------------------------------- JSON (camelCase, 2-space indent)

    public static String json(List<Feature> features, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"title\": ").append(jstr(title)).append(",\n");
        sb.append("  \"features\": ");
        List<Feature> feats = orderedFeatures(features);
        if (feats.isEmpty()) {
            sb.append("[]\n}");
            return sb.toString();
        }
        sb.append("[\n");
        for (int fi = 0; fi < feats.size(); fi++) {
            Feature f = feats.get(fi);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(jstr(f.displayName())).append(",\n");
            sb.append("      \"endpoint\": ").append(jstrOrNull(f.endpoint())).append(",\n");
            sb.append("      \"description\": ").append(jstrOrNull(f.description())).append(",\n");
            sb.append("      \"labels\": ").append(jstrArray(f.labels(), "      ")).append(",\n");
            sb.append("      \"scenarios\": ");
            List<Scenario> scs = orderedScenarios(f);
            if (scs.isEmpty()) {
                sb.append("[]\n");
            } else {
                sb.append("[\n");
                for (int si = 0; si < scs.size(); si++) {
                    Scenario s = scs.get(si);
                    sb.append("        {\n");
                    sb.append("          \"name\": ").append(jstr(s.name())).append(",\n");
                    sb.append("          \"isHappyPath\": ").append(s.isHappyPath()).append(",\n");
                    sb.append("          \"labels\": ").append(jstrArray(s.labels(), "          ")).append(",\n");
                    sb.append("          \"categories\": ").append(jstrArray(s.categories(), "          "))
                        .append(",\n");
                    sb.append("          \"steps\": ").append(jstepArray(s.steps(), "          ")).append('\n');
                    sb.append("        }").append(si < scs.size() - 1 ? ",\n" : "\n");
                }
                sb.append("      ]\n");
            }
            sb.append("    }").append(fi < feats.size() - 1 ? ",\n" : "\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    private static String jstepArray(List<ScenarioStep> steps, String indent) {
        List<String> texts = new ArrayList<>();
        for (ScenarioStep step : steps) {
            texts.add(stepText(step));
        }
        return jstrArray(texts, indent);
    }

    private static String jstrArray(List<String> values, String indent) {
        if (values.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < values.size(); i++) {
            sb.append(indent).append("  ").append(jstr(values.get(i)));
            sb.append(i < values.size() - 1 ? ",\n" : "\n");
        }
        sb.append(indent).append(']');
        return sb.toString();
    }

    private static String jstrOrNull(String value) {
        return value == null ? "null" : jstr(value);
    }

    private static String jstr(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // ---------------------------------------------------------------- XML

    public static String xml(List<Feature> features, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("<Specifications>\n");
        sb.append("  <Title>").append(xesc(title)).append("</Title>\n");
        sb.append("  <Features>\n");
        for (Feature f : orderedFeatures(features)) {
            sb.append("    <Feature>\n");
            sb.append("      <Name>").append(xesc(f.displayName())).append("</Name>\n");
            if (f.endpoint() != null) {
                sb.append("      <Endpoint>").append(xesc(f.endpoint())).append("</Endpoint>\n");
            }
            if (f.description() != null) {
                sb.append("      <Description>").append(xesc(f.description())).append("</Description>\n");
            }
            if (!f.labels().isEmpty()) {
                sb.append("      <Labels>\n");
                for (String l : f.labels()) {
                    sb.append("        <Label>").append(xesc(l)).append("</Label>\n");
                }
                sb.append("      </Labels>\n");
            }
            sb.append("      <Scenarios>\n");
            for (Scenario s : orderedScenarios(f)) {
                sb.append("        <Scenario>\n");
                sb.append("          <Name>").append(xesc(s.name())).append("</Name>\n");
                sb.append("          <IsHappyPath>").append(s.isHappyPath()).append("</IsHappyPath>\n");
                if (!s.labels().isEmpty()) {
                    sb.append("          <Labels>\n");
                    for (String l : s.labels()) {
                        sb.append("            <Label>").append(xesc(l)).append("</Label>\n");
                    }
                    sb.append("          </Labels>\n");
                }
                if (!s.categories().isEmpty()) {
                    sb.append("          <Categories>\n");
                    for (String c : s.categories()) {
                        sb.append("            <Category>").append(xesc(c)).append("</Category>\n");
                    }
                    sb.append("          </Categories>\n");
                }
                if (!s.steps().isEmpty()) {
                    sb.append("          <Steps>\n");
                    for (ScenarioStep step : s.steps()) {
                        appendXmlStep(sb, step, "            ");
                    }
                    sb.append("          </Steps>\n");
                }
                sb.append("        </Scenario>\n");
            }
            sb.append("      </Scenarios>\n");
            sb.append("    </Feature>\n");
        }
        sb.append("  </Features>\n");
        sb.append("</Specifications>");
        return sb.toString();
    }

    private static void appendXmlStep(StringBuilder sb, ScenarioStep step, String indent) {
        if (step.subSteps().isEmpty()) {
            sb.append(indent).append("<Step>").append(xesc(stepText(step))).append("</Step>\n");
        } else {
            sb.append(indent).append("<Step>").append(xesc(stepText(step))).append('\n');
            for (ScenarioStep sub : step.subSteps()) {
                appendXmlStep(sb, sub, indent + "  ");
            }
            sb.append(indent).append("</Step>\n");
        }
    }

    private static String xesc(String value) {
        return HtmlEscaper.encode(value);
    }
}
