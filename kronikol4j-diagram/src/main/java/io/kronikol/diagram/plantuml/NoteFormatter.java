package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.Header;
import io.kronikol.diagram.json.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Formats the body of a PlantUML note from an interaction's headers and content (.NET
 * {@code FormatNoteContent}). Headers come first — ordinal-sorted, with the excluded keys dropped, each
 * rendered as a gray {@code <color:gray>[Key=Value]} (batched into &le;80-char chunks like the .NET
 * {@code BatchGray}) — then a blank line, then the canonically pretty-printed content. Applies the §6.5
 * parity discipline ({@code \n} newlines, backslash-doubling note escaping).
 */
public final class NoteFormatter {

    private static final int MAX_NOTE_CHUNK_CHARS = 80; // .NET MaxNoteChunkChars

    private NoteFormatter() {
    }

    /** Escapes text for inclusion in a PlantUML note. Mirrors .NET {@code EscapeForPlantUmlNote}. */
    static String escapeForNote(String text) {
        return text.replace("\\", "\\\\");
    }

    /** As {@link #format(String, List, List, List, Set, Set)} with no focus fields. */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders) {
        return format(content, headers, excludedHeaders, null, Set.of(), Set.of());
    }

    /** As {@link #format(String, List, List, List, Set, Set, UnaryOperator)} with no mid processor. */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders,
                                List<String> focusFields, Set<FocusEmphasis> focusEmphasis,
                                Set<FocusDeEmphasis> focusDeEmphasis) {
        return format(content, headers, excludedHeaders, focusFields, focusEmphasis, focusDeEmphasis, null);
    }

    /** As {@link #format(String, List, List, List, Set, Set, UnaryOperator, boolean)} for a request. */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders,
                                List<String> focusFields, Set<FocusEmphasis> focusEmphasis,
                                Set<FocusDeEmphasis> focusDeEmphasis, UnaryOperator<String> midProcessor) {
        return format(content, headers, excludedHeaders, focusFields, focusEmphasis, focusDeEmphasis,
            midProcessor, true);
    }

    /** As {@link #format(String, List, List, List, Set, Set, UnaryOperator, boolean, GraphQlBodyFormat)}
     *  with the .NET default GraphQL body format. */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders,
                                List<String> focusFields, Set<FocusEmphasis> focusEmphasis,
                                Set<FocusDeEmphasis> focusDeEmphasis, UnaryOperator<String> midProcessor,
                                boolean isRequest) {
        return format(content, headers, excludedHeaders, focusFields, focusEmphasis, focusDeEmphasis,
            midProcessor, isRequest, GraphQlBodyFormat.FORMATTED_WITH_METADATA);
    }

    /** Builds the note body (may be empty): the content (binary → {@code [binary content]}; a GraphQL
     *  request → formatted query [+ variables/extensions]; else pretty-printed JSON or a form-url-encoded
     *  request body; then the {@code midProcessor} hook and focus emphasis/de-emphasis), with the excluded
     *  headers gray-rendered above — unless a GraphQL query-only body suppresses them. */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders,
                                List<String> focusFields, Set<FocusEmphasis> focusEmphasis,
                                Set<FocusDeEmphasis> focusDeEmphasis, UnaryOperator<String> midProcessor,
                                boolean isRequest, GraphQlBodyFormat graphQlBodyFormat) {
        Body body = formatBody(content, isRequest, graphQlBodyFormat, focusFields);
        String formattedContent = body.content();
        if (midProcessor != null) { // .NET midFormattingProcessor — after formatting, before focus
            formattedContent = midProcessor.apply(formattedContent);
        }
        if (focusFields != null && !focusFields.isEmpty()) {
            formattedContent = JsonFocusFormatter.formatWithFocus(formattedContent, focusFields,
                focusEmphasis, focusDeEmphasis);
        }

        String headersOnTop = "";
        if (!body.suppressHeaders() && headers != null && !headers.isEmpty()) {
            headersOnTop = headers.stream()
                .filter(h -> excludedHeaders == null || !excludedHeaders.contains(h.key()))
                .sorted(Comparator.comparing(Header::key)) // String.compareTo is ordinal (§6.5)
                .flatMap(h -> batchGray("[" + h.key() + "=" + (h.value() == null ? "" : h.value()) + "]").stream())
                .collect(Collectors.joining("\n"));
        }

        // .NET: ((headersOnTop + "\n\n").TrimStart() + formattedContent.Trim()).TrimEnd()
        String combined = (headersOnTop + "\n\n").stripLeading() + formattedContent.strip();
        return escapeForNote(combined.stripTrailing());
    }

    private record Body(String content, boolean suppressHeaders) {
    }

    /** Formats a content body (.NET {@code FormatNoteContent} body path): binary content becomes a
     *  placeholder; a GraphQL request becomes its formatted query (suppressing headers in query-only
     *  mode); else JSON is pretty-printed, a request body is form-url-encoded, and a response is verbatim. */
    private static Body formatBody(String content, boolean isRequest, GraphQlBodyFormat graphQlBodyFormat,
                                   List<String> focusFields) {
        if (content == null || content.isBlank()) {
            return new Body("", false);
        }
        String body = isBinaryContent(content) ? "<i>[binary content]</i>" : content;
        boolean noFocus = focusFields == null || focusFields.isEmpty();
        if (isRequest && graphQlBodyFormat != GraphQlBodyFormat.JSON && noFocus) {
            String gql = GraphQlBodyFormatter.tryFormat(body, graphQlBodyFormat);
            if (gql != null) {
                return new Body(gql, graphQlBodyFormat == GraphQlBodyFormat.FORMATTED_QUERY_ONLY);
            }
        }
        String pretty = Json.tryPrettyPrint(body);
        if (pretty != null) {
            return new Body(pretty, false);
        }
        return new Body(isRequest ? formatFormUrlEncoded(body) : body, false);
    }

    /** .NET {@code IsBinaryContent}: &gt;10% non-tab/newline control chars in the first 512 chars. */
    private static boolean isBinaryContent(String content) {
        int checkLength = Math.min(content.length(), 512);
        int controlCount = 0;
        for (int i = 0; i < checkLength; i++) {
            char c = content.charAt(i);
            if (c != '\t' && c != '\n' && c != '\r' && c < ' ') {
                controlCount++;
            }
        }
        return controlCount > checkLength * 0.1;
    }

    /** .NET {@code FormatFormUrlEncodedContent}: each {@code &}-separated field on its own line, the
     *  separator rendered as a gray {@code <font color="lightgray">&} after every field but the last. */
    private static String formatFormUrlEncoded(String content) {
        String divider = "<font color=\"lightgray\">&";
        List<String> out = new ArrayList<>();
        for (String part : content.split("&", -1)) {
            List<String> chunks = chunk(part);
            if (chunks.isEmpty()) {
                continue;
            }
            chunks.set(chunks.size() - 1, chunks.get(chunks.size() - 1) + divider);
            out.addAll(chunks);
        }
        String joined = String.join("\n", out);
        return joined.endsWith(divider) ? joined.substring(0, joined.length() - divider.length()) : joined;
    }

    /** Splits {@code value} into &le;80-char chunks (.NET {@code ChunksUpTo(MaxNoteChunkChars)}). */
    private static List<String> chunk(String value) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < value.length(); i += MAX_NOTE_CHUNK_CHARS) {
            chunks.add(value.substring(i, Math.min(i + MAX_NOTE_CHUNK_CHARS, value.length())));
        }
        return chunks;
    }

    /** Splits {@code value} into &le;80-char chunks, each prefixed {@code <color:gray>} (.NET {@code BatchGray}). */
    private static List<String> batchGray(String value) {
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < value.length(); i += MAX_NOTE_CHUNK_CHARS) {
            chunks.add("<color:gray>" + value.substring(i, Math.min(i + MAX_NOTE_CHUNK_CHARS, value.length())));
        }
        return chunks;
    }
}
