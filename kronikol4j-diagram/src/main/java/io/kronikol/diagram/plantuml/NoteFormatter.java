package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.Header;
import io.kronikol.diagram.json.Json;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /** Builds the note body (may be empty), excluding the given header keys (.NET defaults to
     *  {@code Cache-Control}/{@code Pragma}). */
    public static String format(String content, List<Header> headers, List<String> excludedHeaders) {
        String headersOnTop = "";
        if (headers != null && !headers.isEmpty()) {
            headersOnTop = headers.stream()
                .filter(h -> excludedHeaders == null || !excludedHeaders.contains(h.key()))
                .sorted(Comparator.comparing(Header::key)) // String.compareTo is ordinal (§6.5)
                .flatMap(h -> batchGray("[" + h.key() + "=" + (h.value() == null ? "" : h.value()) + "]").stream())
                .collect(Collectors.joining("\n"));
        }

        String formattedContent = "";
        if (content != null && !content.isBlank()) {
            String pretty = Json.tryPrettyPrint(content);
            formattedContent = pretty != null ? pretty : content;
        }

        // .NET: ((headersOnTop + "\n\n").TrimStart() + formattedContent.Trim()).TrimEnd()
        String combined = (headersOnTop + "\n\n").stripLeading() + formattedContent.strip();
        return escapeForNote(combined.stripTrailing());
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
