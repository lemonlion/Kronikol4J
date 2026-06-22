package io.kronikol.diagram.plantuml;

/**
 * How non-focused JSON fields are de-emphasised in diagram notes — a port of the .NET {@code [Flags]
 * FocusDeEmphasis}. Combine with {@link java.util.EnumSet}; the .NET default is {@link #LIGHT_GRAY}.
 */
public enum FocusDeEmphasis {
    /** Non-focused fields are rendered in light gray. */
    LIGHT_GRAY,
    /** Non-focused fields are rendered with a smaller font size. */
    SMALLER_TEXT,
    /** Non-focused fields are hidden entirely. */
    HIDDEN
}
