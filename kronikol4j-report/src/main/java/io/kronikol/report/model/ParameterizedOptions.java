package io.kronikol.report.model;

/**
 * The parameterized-test rendering options, mirroring the corresponding .NET {@code GenerateHtmlReport}
 * parameters. {@link #DEFAULTS} matches .NET's defaults (group on, ≤10 columns, titleized names) and is
 * used by the report's back-compatible {@code render(...)} overloads, so existing output is unchanged.
 *
 * @param groupParameterizedTests when false, only framework-provided {@code outlineId} groups form;
 *                                the display-name-prefix grouping pass is skipped (.NET {@code enabled})
 * @param maxParameterColumns     the column cap above which a group falls back to the single "Test Case"
 *                                column (.NET {@code maxParameterColumns})
 * @param titleizeParameterNames  when true, parameter column headers are Titleized (.NET
 *                                {@code titleizeParameterNames})
 */
public record ParameterizedOptions(
    boolean groupParameterizedTests, int maxParameterColumns, boolean titleizeParameterNames) {

    /** The .NET defaults: grouping on, ≤10 columns, names titleized. */
    public static final ParameterizedOptions DEFAULTS = new ParameterizedOptions(true, 10, true);
}
