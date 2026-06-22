package io.kronikol.diagram.plantuml;

/**
 * How focused JSON fields are emphasised in diagram notes — a port of the .NET {@code [Flags]
 * FocusEmphasis}. Combine with {@link java.util.EnumSet}; the .NET default is {@link #BOLD}.
 */
public enum FocusEmphasis {
    /** Focused fields are rendered in bold. */
    BOLD,
    /** Focused fields are rendered with a highlight colour. */
    COLORED
}
