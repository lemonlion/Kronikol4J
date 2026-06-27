package io.kronikol.core.tracking;

import java.util.List;

/**
 * Ambient focus configuration for diagram notes: marks specific request/response JSON field names for
 * emphasis (bold / colour, per the diagram's {@code FocusEmphasis}/{@code FocusDeEmphasis}) in the next
 * tracked interaction, making key data stand out. Java port of the .NET {@code DiagramFocus}.
 *
 * <p>{@link #request}/{@link #response} stash the field names on a {@link ThreadLocal}; the tracker consumes
 * them <em>once</em> (consume-once: {@link #consumePendingRequestFocus}/{@link #consumePendingResponseFocus}
 * return the names and clear them) when it builds the {@link RequestResponseLog} ({@code focusFields}). The
 * .NET typed {@code Request<T>(x => x.Field)} expression overloads have no Java analog (no expression trees) —
 * use the string-field-name forms.
 */
public final class DiagramFocus {

    private static final ThreadLocal<List<String>> PENDING_REQUEST = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PENDING_RESPONSE = new ThreadLocal<>();

    private DiagramFocus() {
    }

    /** Marks {@code fieldNames} for emphasis in the next tracked request note. */
    public static void request(String... fieldNames) {
        PENDING_REQUEST.set(copyOf(fieldNames));
    }

    /** Marks {@code fieldNames} for emphasis in the next tracked response note. */
    public static void response(String... fieldNames) {
        PENDING_RESPONSE.set(copyOf(fieldNames));
    }

    /** Returns and clears the pending request focus fields, or {@code null} if none were set. */
    public static List<String> consumePendingRequestFocus() {
        List<String> v = PENDING_REQUEST.get();
        PENDING_REQUEST.remove();
        return v;
    }

    /** Returns and clears the pending response focus fields, or {@code null} if none were set. */
    public static List<String> consumePendingResponseFocus() {
        List<String> v = PENDING_RESPONSE.get();
        PENDING_RESPONSE.remove();
        return v;
    }

    /** Clears any pending request and response focus (mandatory teardown for ambient state, §3.2). */
    public static void clearAll() {
        PENDING_REQUEST.remove();
        PENDING_RESPONSE.remove();
    }

    private static List<String> copyOf(String[] fieldNames) {
        return fieldNames == null ? List.of() : List.of(fieldNames);
    }
}
