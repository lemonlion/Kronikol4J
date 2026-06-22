package io.kronikol.diagram.plantuml;

import java.util.function.UnaryOperator;

/**
 * Caller-supplied content processors for diagram notes — a port of the .NET {@code Func<string,string>}
 * formatting hooks. Each request/response stage is optional ({@code null} = no-op):
 * <ul>
 *   <li><b>pre</b> — transforms the raw content before formatting;</li>
 *   <li><b>mid</b> — transforms the formatted (pretty-printed) content, before focus emphasis;</li>
 *   <li><b>post</b> — transforms the final note body.</li>
 * </ul>
 * The functions are supplied by the caller (an environment-integration hook, the same boundary class as
 * the OpenTelemetry span capture), so only the hook <em>placement</em> is ported — not any specific
 * function. {@link #NONE} applies no processing.
 */
public record NoteProcessors(
    UnaryOperator<String> requestPre, UnaryOperator<String> requestMid, UnaryOperator<String> requestPost,
    UnaryOperator<String> responsePre, UnaryOperator<String> responseMid, UnaryOperator<String> responsePost) {

    public static final NoteProcessors NONE = new NoteProcessors(null, null, null, null, null, null);

    /** Applies a (possibly null) processor to a value. */
    static String apply(UnaryOperator<String> processor, String value) {
        return processor == null ? value : processor.apply(value);
    }
}
