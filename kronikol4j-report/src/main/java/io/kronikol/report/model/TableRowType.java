package io.kronikol.report.model;

/** The type of row in a tabular step parameter (mirrors the .NET {@code TableRowType}). */
public enum TableRowType {
    /** The row matches an expected row. */
    MATCHING,
    /** The row exists in the actual result but was not expected. */
    SURPLUS,
    /** The row was expected but not found in the actual result. */
    MISSING
}
