package io.kronikol.core.tracking;

/**
 * Classifies how an interaction should be rendered.
 *
 * <p>Mirrors the .NET {@code RequestResponseMetaType} enum.
 */
public enum RequestResponseMetaType {
    /** Standard request/response pair. */
    DEFAULT,
    /** Fire-and-forget event (message publish, event send) — produces two logs with the same ids. */
    EVENT
}
