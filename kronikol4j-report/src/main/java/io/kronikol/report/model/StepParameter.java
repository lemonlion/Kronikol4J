package io.kronikol.report.model;

/**
 * A parameter associated with a test step, rendered as inline text, a table, or a tree (mirrors the
 * .NET {@code StepParameter} + {@code StepParameterKind}).
 */
public record StepParameter(String name, Kind kind, InlineParameterValue inlineValue,
                            TabularParameterValue tabularValue, TreeParameterValue treeValue) {

    /** The display style for a step parameter. */
    public enum Kind {
        /** A simple inline text value. */
        INLINE,
        /** A tabular value with columns and rows. */
        TABULAR,
        /** A tree-structured value with nested nodes. */
        TREE
    }

    /** An inline parameter (name + value). */
    public static StepParameter inline(String name, InlineParameterValue value) {
        return new StepParameter(name, Kind.INLINE, value, null, null);
    }

    /** A tabular parameter (name + table). */
    public static StepParameter tabular(String name, TabularParameterValue value) {
        return new StepParameter(name, Kind.TABULAR, null, value, null);
    }

    /** A tree parameter (name + tree). */
    public static StepParameter tree(String name, TreeParameterValue value) {
        return new StepParameter(name, Kind.TREE, null, null, value);
    }
}
