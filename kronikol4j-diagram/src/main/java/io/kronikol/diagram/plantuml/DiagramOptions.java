package io.kronikol.diagram.plantuml;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diagram-generation styling options for {@link PlantUmlCreator}, mirroring the .NET
 * {@code PlantUmlCreator.Create} parameters: arrow/participant colours, the {@code !theme} directive,
 * the headers excluded from notes (and {@code excludeAllHeaders}), setup/assertion separation + highlight,
 * focus-field emphasis/de-emphasis, the GraphQL body format, internal-flow tracking, note truncation, and
 * the {@code dependencyColors}/{@code serviceTypeOverrides} override maps. {@link #defaults()} matches the
 * .NET defaults.
 */
public record DiagramOptions(
    boolean arrowColors, boolean participantColors, String plantUmlTheme,
    List<String> excludedHeaders, boolean separateSetup, boolean highlightSetup, String setupHighlightColor,
    Set<FocusEmphasis> focusEmphasis, Set<FocusDeEmphasis> focusDeEmphasis,
    GraphQlBodyFormat graphQlBodyFormat, boolean internalFlowTracking,
    int truncateNotesAfterLines, boolean excludeAllHeaders,
    Map<String, String> dependencyColors, Map<String, String> serviceTypeOverrides) {

    /** The .NET {@code DefaultExcludedHeaders}. */
    public static final List<String> DEFAULT_EXCLUDED_HEADERS = List.of("Cache-Control", "Pragma");

    public DiagramOptions {
        excludedHeaders = excludedHeaders == null ? DEFAULT_EXCLUDED_HEADERS : List.copyOf(excludedHeaders);
        focusEmphasis = focusEmphasis == null ? Set.of(FocusEmphasis.BOLD) : Set.copyOf(focusEmphasis);
        focusDeEmphasis = focusDeEmphasis == null
            ? Set.of(FocusDeEmphasis.LIGHT_GRAY) : Set.copyOf(focusDeEmphasis);
        graphQlBodyFormat = graphQlBodyFormat == null
            ? GraphQlBodyFormat.FORMATTED_WITH_METADATA : graphQlBodyFormat;
        dependencyColors = dependencyColors == null ? Map.of() : Map.copyOf(dependencyColors);
        serviceTypeOverrides = serviceTypeOverrides == null ? Map.of() : Map.copyOf(serviceTypeOverrides);
    }

    /** The .NET defaults (internal-flow tracking off, no note truncation, no header/colour/type overrides). */
    public static DiagramOptions defaults() {
        return new DiagramOptions(true, false, null, DEFAULT_EXCLUDED_HEADERS, false, true, null,
            EnumSet.of(FocusEmphasis.BOLD), EnumSet.of(FocusDeEmphasis.LIGHT_GRAY),
            GraphQlBodyFormat.FORMATTED_WITH_METADATA, false, 0, false, Map.of(), Map.of());
    }

    /** Colours only (other options at .NET defaults). */
    public static DiagramOptions colours(boolean arrowColors, boolean participantColors) {
        return defaults().withArrowColors(arrowColors).withParticipantColors(participantColors);
    }

    public DiagramOptions withArrowColors(boolean v) {
        return new DiagramOptions(v, participantColors, plantUmlTheme, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withParticipantColors(boolean v) {
        return new DiagramOptions(arrowColors, v, plantUmlTheme, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withPlantUmlTheme(String v) {
        return new DiagramOptions(arrowColors, participantColors, v, excludedHeaders, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withExcludedHeaders(List<String> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, v, separateSetup,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withSeparateSetup(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders, v,
            highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withHighlightSetup(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, v, setupHighlightColor, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withSetupHighlightColor(String v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, v, focusEmphasis, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withFocusEmphasis(Set<FocusEmphasis> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, v, focusDeEmphasis, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withFocusDeEmphasis(Set<FocusDeEmphasis> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, v, graphQlBodyFormat,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withGraphQlBodyFormat(GraphQlBodyFormat v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis, v,
            internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withInternalFlowTracking(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis,
            graphQlBodyFormat, v, truncateNotesAfterLines, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withTruncateNotesAfterLines(int v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis,
            graphQlBodyFormat, internalFlowTracking, v, excludeAllHeaders, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withExcludeAllHeaders(boolean v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis,
            graphQlBodyFormat, internalFlowTracking, truncateNotesAfterLines, v, dependencyColors,
            serviceTypeOverrides);
    }

    public DiagramOptions withDependencyColors(Map<String, String> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis,
            graphQlBodyFormat, internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders, v,
            serviceTypeOverrides);
    }

    public DiagramOptions withServiceTypeOverrides(Map<String, String> v) {
        return new DiagramOptions(arrowColors, participantColors, plantUmlTheme, excludedHeaders,
            separateSetup, highlightSetup, setupHighlightColor, focusEmphasis, focusDeEmphasis,
            graphQlBodyFormat, internalFlowTracking, truncateNotesAfterLines, excludeAllHeaders,
            dependencyColors, v);
    }
}
