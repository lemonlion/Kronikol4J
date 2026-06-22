package io.kronikol.diagram.plantuml;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Diagram-generation styling options for {@link PlantUmlCreator}, mirroring the .NET
 * {@code PlantUmlCreator.Create} parameters: arrow/participant colours, the {@code !theme} directive,
 * the headers excluded from notes, setup/assertion separation + highlight, focus-field
 * emphasis/de-emphasis, and the GraphQL body format. {@link #defaults()} matches the .NET defaults.
 */
public record DiagramOptions(
    boolean arrowColors, boolean participantColors, String plantUmlTheme,
    List<String> excludedHeaders, boolean separateSetup, boolean highlightSetup, String setupHighlightColor,
    Set<FocusEmphasis> focusEmphasis, Set<FocusDeEmphasis> focusDeEmphasis,
    GraphQlBodyFormat graphQlBodyFormat) {

    /** The .NET {@code DefaultExcludedHeaders}. */
    public static final List<String> DEFAULT_EXCLUDED_HEADERS = List.of("Cache-Control", "Pragma");

    public DiagramOptions {
        excludedHeaders = excludedHeaders == null ? DEFAULT_EXCLUDED_HEADERS : List.copyOf(excludedHeaders);
        focusEmphasis = focusEmphasis == null ? Set.of(FocusEmphasis.BOLD) : Set.copyOf(focusEmphasis);
        focusDeEmphasis = focusDeEmphasis == null
            ? Set.of(FocusDeEmphasis.LIGHT_GRAY) : Set.copyOf(focusDeEmphasis);
        graphQlBodyFormat = graphQlBodyFormat == null
            ? GraphQlBodyFormat.FORMATTED_WITH_METADATA : graphQlBodyFormat;
    }

    /** The .NET defaults. */
    public static DiagramOptions defaults() {
        return new DiagramOptions(true, false, null, DEFAULT_EXCLUDED_HEADERS, false, true, null,
            EnumSet.of(FocusEmphasis.BOLD), EnumSet.of(FocusDeEmphasis.LIGHT_GRAY),
            GraphQlBodyFormat.FORMATTED_WITH_METADATA);
    }

    /** Colours only (other options at .NET defaults). */
    public static DiagramOptions colours(boolean arrowColors, boolean participantColors) {
        return defaults().withArrowColors(arrowColors).withParticipantColors(participantColors);
    }

    public DiagramOptions withArrowColors(boolean v) {
        return new DiagramOptions(v, participantColors, plantUmlTheme, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withParticipantColors(boolean v) {
        return new DiagramOptions(arrowColors, v, plantUmlTheme, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withPlantUmlTheme(String v) {
        return new DiagramOptions(arrowColors, participantColors, v, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withExcludedHeaders(List<String> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, v, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withSeparateSetup(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders, v,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withHighlightSetup(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, v, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withSetupHighlightColor(String v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, v, focusEmphasis, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withFocusEmphasis(Set<FocusEmphasis> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, v, focusDeEmphasis, graphQlBodyFormat);
    }

    public DiagramOptions withFocusDeEmphasis(Set<FocusDeEmphasis> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, v, graphQlBodyFormat);
    }

    public DiagramOptions withGraphQlBodyFormat(GraphQlBodyFormat v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, v);
    }
}
