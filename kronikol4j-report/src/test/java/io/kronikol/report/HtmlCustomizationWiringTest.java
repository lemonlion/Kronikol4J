package io.kronikol.report;

import static org.assertj.core.api.Assertions.assertThat;

import io.kronikol.report.model.Feature;
import io.kronikol.report.model.HtmlCustomization;
import io.kronikol.report.model.Scenario;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies that {@link HtmlCustomization} (CSS / favicon / logo / step numbers) flows from
 * {@link ReportOptions} — including its system-property form — through {@link HtmlReportGenerator} into the
 * rendered HTML. Closes the Tier-2 "HTML customization wiring" gap (the model existed but was never passed
 * through the generation path).
 */
class HtmlCustomizationWiringTest {

    private static List<Feature> oneFeature() {
        return List.of(new Feature("Checkout", List.of(Scenario.passed("Checkout succeeds", "t1"))));
    }

    @Test
    void generateAppliesHtmlCustomizationFromReportOptions(@TempDir Path dir) throws IOException {
        HtmlCustomization custom = new HtmlCustomization(
            null, ".x{color:red}", "data:image/png;base64,ZZ", "<img src='logo.png'>", true, false,
            ".y{color:blue}");
        ReportOptions options = ReportOptions.defaults().withHtmlCustomization(custom);

        var report = HtmlReportGenerator.generate(oneFeature(), List.of(), dir, "Demo Run", options);
        String html = Files.readString(report.htmlFile());

        assertThat(html)
            .contains("<style>.x{color:red}</style>")               // customCss trailing block
            .contains(".y{color:blue}")                              // customStyleSheet in main <style>
            .contains("<div class=\"custom-logo\"><img src='logo.png'></div>")
            .contains("data:image/png;base64,ZZ");                   // favicon href override
    }

    @Test
    void defaultCustomizationIsNoneAndRoundTrips() {
        assertThat(ReportOptions.defaults().customization()).isEqualTo(HtmlCustomization.NONE);
        HtmlCustomization c = new HtmlCustomization(null, ".a{}", null, null, false, false, null);
        assertThat(ReportOptions.defaults().withHtmlCustomization(c).customization()).isSameAs(c);
        assertThat(ReportOptions.defaults().withHtmlCustomization(null).customization())
            .isEqualTo(HtmlCustomization.NONE);
    }

    @Test
    void customizationSurvivesOtherWithers() {
        HtmlCustomization c = new HtmlCustomization(null, ".a{}", null, null, true, false, null);
        ReportOptions o = ReportOptions.defaults()
            .withHtmlCustomization(c)
            .withArrowColors(true)
            .withGenerateSchema(true)
            .withDataFormats(java.util.Set.of());
        assertThat(o.customization()).isEqualTo(c); // not dropped by unrelated withers
    }

    @Test
    void fromSystemPropertiesReadsCustomizationProps() {
        Map<String, String> props = Map.of(
            ReportOptions.CUSTOM_CSS_PROPERTY, ".sp{color:green}",
            ReportOptions.CUSTOM_LOGO_PROPERTY, "<b>Logo</b>",
            ReportOptions.CUSTOM_FAVICON_PROPERTY, "data:fav",
            ReportOptions.SHOW_STEP_NUMBERS_PROPERTY, "true",
            ReportOptions.CUSTOM_STYLESHEET_PROPERTY, ".ss{}");
        withSystemProperties(props, () -> {
            HtmlCustomization c = ReportOptions.customizationFromSystemProperties();
            assertThat(c.customCss()).isEqualTo(".sp{color:green}");
            assertThat(c.customLogoHtml()).isEqualTo("<b>Logo</b>");
            assertThat(c.customFaviconBase64()).isEqualTo("data:fav");
            assertThat(c.showStepNumbers()).isTrue();
            assertThat(c.customStyleSheet()).isEqualTo(".ss{}");
            assertThat(ReportOptions.fromSystemProperties().customization()).isEqualTo(c);
        });
    }

    @Test
    void fromSystemPropertiesReturnsNoneWhenNoCustomizationProps() {
        assertThat(ReportOptions.customizationFromSystemProperties()).isEqualTo(HtmlCustomization.NONE);
    }

    private static void withSystemProperties(Map<String, String> props, Runnable body) {
        Map<String, String> previous = new java.util.HashMap<>();
        props.forEach((k, v) -> {
            previous.put(k, System.getProperty(k));
            System.setProperty(k, v);
        });
        try {
            body.run();
        } finally {
            previous.forEach((k, v) -> {
                if (v == null) {
                    System.clearProperty(k);
                } else {
                    System.setProperty(k, v);
                }
            });
        }
    }
}
