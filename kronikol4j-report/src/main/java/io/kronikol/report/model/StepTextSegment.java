package io.kronikol.report.model;

/**
 * A segment of step text — either literal prose, an inline parameter value, or a reference to a
 * tabular/tree parameter rendered below the step (mirrors the .NET {@code StepTextSegment}). When a
 * step carries text segments the renderer uses them instead of the plain {@code text}, producing
 * highlighted inline parameter values within the prose.
 */
public record StepTextSegment(String text, InlineParameterValue parameter, String parameterName,
                              String tableReference, String tableReferenceFormattedValue) {

    /** A literal text segment. */
    public static StepTextSegment literal(String text) {
        return new StepTextSegment(text, null, null, null, null);
    }

    /** An inline parameter segment with value, verification status, and name (for the tooltip). */
    public static StepTextSegment param(String name, InlineParameterValue value) {
        return new StepTextSegment(null, value, name, null, null);
    }

    /** A table/tree parameter reference segment (renders as a clickable toggle). */
    public static StepTextSegment tableRef(String paramName) {
        return new StepTextSegment(null, null, null, paramName, null);
    }

    /** A table/tree reference segment with a formatted-value fallback when no backing parameter exists. */
    public static StepTextSegment tableRef(String paramName, String formattedValue) {
        return new StepTextSegment(null, null, null, paramName, formattedValue);
    }
}
