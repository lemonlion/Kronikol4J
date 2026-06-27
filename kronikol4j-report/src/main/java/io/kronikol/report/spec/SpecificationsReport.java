package io.kronikol.report.spec;

import io.kronikol.report.HtmlReportGenerator;
import io.kronikol.report.model.Feature;
import io.kronikol.report.model.HtmlCustomization;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Emits the Specifications report — the "living documentation" view of the same test data, as a separate
 * {@code Specifications.html} (the standard HTML report re-rendered with step numbers, the specifications
 * stylesheet, and blank-on-failure) plus a {@code Specifications.<ext>} data file ({@link SpecificationsData}).
 * Java port of the .NET {@code ReportGenerator} specifications path. Distinct from {@code TestRunReport}.
 */
public final class SpecificationsReport {

    /** The HTML + data files written (either may be {@code null} if its generation was disabled). */
    public record Generated(Path htmlFile, Path dataFile) {
    }

    private SpecificationsReport() {
    }

    /** Renders the Specifications HTML string (the standard report with spec customization). */
    public static String renderHtml(List<Feature> features, Map<String, String> diagramByTestId,
                                    SpecificationsOptions options) {
        HtmlCustomization custom = new HtmlCustomization(
            null, null, null, null, options.showStepNumbers(), true, options.customStyleSheet());
        return HtmlReportGenerator.renderHtml(features, diagramByTestId, null, options.title(), custom);
    }

    /** Writes {@code Specifications.html} + {@code Specifications.<ext>} to {@code outputDir} per {@code options}. */
    public static Generated write(Path outputDir, List<Feature> features,
                                  Map<String, String> diagramByTestId, SpecificationsOptions options)
            throws IOException {
        Files.createDirectories(outputDir);
        Path htmlFile = null;
        Path dataFile = null;

        if (options.generateReport()) {
            String html = renderHtml(features, diagramByTestId, options);
            htmlFile = outputDir.resolve(options.htmlFileName() + ".html");
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        }
        if (options.generateData()) {
            String data = SpecificationsData.generate(features, options.title(), options.dataFormat());
            dataFile = outputDir.resolve(options.dataFileName() + "." + options.dataFormat().extension());
            Files.writeString(dataFile, data, StandardCharsets.UTF_8);
        }
        return new Generated(htmlFile, dataFile);
    }
}
