package io.kronikol.diagram.plantuml;

import io.kronikol.core.tracking.Header;
import io.kronikol.diagram.json.Json;
import java.util.Comparator;
import java.util.List;

/**
 * Formats the body of a PlantUML note from an interaction's headers and content. Applies the §6.5
 * parity discipline: ordinal header sort, canonical JSON pretty-printing, {@code \n} newlines, and
 * PlantUML note escaping (backslash doubling).
 */
public final class NoteFormatter {

    private NoteFormatter() {
    }

    /** Escapes text for inclusion in a PlantUML note. Mirrors .NET {@code EscapeForPlantUmlNote}. */
    static String escapeForNote(String text) {
        return text.replace("\\", "\\\\");
    }

    /** Builds the note body (may be empty). Headers first (ordinal-sorted), then content. */
    public static String format(String content, List<Header> headers) {
        StringBuilder sb = new StringBuilder();

        if (headers != null && !headers.isEmpty()) {
            headers.stream()
                .sorted(Comparator.comparing(Header::key)) // String.compareTo is ordinal (§6.5)
                .forEach(h -> sb.append(h.key()).append(": ")
                    .append(h.value() == null ? "" : h.value()).append('\n'));
            sb.append('\n');
        }

        if (content != null && !content.isBlank()) {
            String pretty = Json.tryPrettyPrint(content);
            sb.append(pretty != null ? pretty : content);
        }

        return escapeForNote(sb.toString().strip());
    }
}
