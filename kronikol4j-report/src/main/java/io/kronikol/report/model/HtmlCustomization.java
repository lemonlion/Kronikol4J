package io.kronikol.report.model;

/**
 * Optional HTML-report customization, mirroring the corresponding .NET {@code GenerateHtmlReport}
 * parameters: CI metadata, custom CSS / favicon / logo, step numbering, and the
 * generate-blank-on-failure switch. {@link #NONE} is the all-default carrier used by the report's
 * back-compatible {@code render(...)} overloads.
 *
 * @param ciMetadata                 CI metadata table (Test Execution Summary); null = omitted
 * @param customCss                  extra CSS injected after the main stylesheet; null = none
 * @param customFaviconBase64        favicon {@code href} override; null = the default data-URI favicon
 * @param customLogoHtml             logo HTML placed above the {@code <h1>}; null = none
 * @param showStepNumbers            prefix steps with their 1-based number (e.g. {@code "1."})
 * @param generateBlankOnFailedTests when true, the report is an empty string if any scenario failed
 */
public record HtmlCustomization(
    CiMetadata ciMetadata, String customCss, String customFaviconBase64, String customLogoHtml,
    boolean showStepNumbers, boolean generateBlankOnFailedTests) {

    /** The all-default customization (no CI block, no custom assets, no step numbers). */
    public static final HtmlCustomization NONE =
        new HtmlCustomization(null, null, null, null, false, false);
}
