package io.kronikol.diagram.plantuml;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Applies focus emphasis/de-emphasis PlantUML markup to specific JSON fields within diagram note
 * content — a faithful port of the .NET {@code JsonFocusFormatter}. Operates line-by-line on the
 * already-pretty-printed JSON: each line is annotated {@code STRUCTURAL}/{@code FOCUSED}/
 * {@code NON_FOCUSED} (a focused property and its descendants stay focused until a sibling resets the
 * state), then focused lines are emphasised ({@code <b>}/{@code <color:blue>}), non-focused lines are
 * de-emphasised ({@code <color:lightgray>}/{@code <size:9>}) or, when {@link FocusDeEmphasis#HIDDEN} is
 * set, collapsed to a single {@code  ...} ellipsis per run.
 */
public final class JsonFocusFormatter {

    private JsonFocusFormatter() {
    }

    private enum LineType { STRUCTURAL, FOCUSED, NON_FOCUSED }

    public static String formatWithFocus(String prettyPrintedJson, List<String> focusFields,
                                         Set<FocusEmphasis> emphasis, Set<FocusDeEmphasis> deEmphasis) {
        if (focusFields == null || focusFields.isEmpty()) {
            return prettyPrintedJson;
        }
        String[] lines = prettyPrintedJson.split("\n", -1);
        if (lines.length < 3) { // at minimum: { field }
            return prettyPrintedJson;
        }
        Set<String> focusSet = new HashSet<>();
        for (String f : focusFields) {
            focusSet.add(f.toLowerCase(Locale.ROOT)); // .NET OrdinalIgnoreCase
        }

        LineType[] annotations = annotateLines(lines, focusSet);
        boolean anyFocused = false;
        for (LineType a : annotations) {
            if (a == LineType.FOCUSED) {
                anyFocused = true;
                break;
            }
        }
        if (!anyFocused) {
            return prettyPrintedJson;
        }

        return deEmphasis.contains(FocusDeEmphasis.HIDDEN)
            ? buildHiddenOutput(lines, annotations, emphasis)
            : buildFormattedOutput(lines, annotations, emphasis, deEmphasis);
    }

    private static LineType[] annotateLines(String[] lines, Set<String> focusSet) {
        LineType[] annotations = new LineType[lines.length];
        LineType currentFocusState = LineType.STRUCTURAL;
        int nestingDepth = 0;
        int stateSetAtDepth = 0;

        for (int i = 0; i < lines.length; i++) {
            String trimmed = stripLeading(lines[i]);

            if (trimmed.equals("{") || trimmed.equals("}") || trimmed.equals("[") || trimmed.equals("]")
                || trimmed.equals("},") || trimmed.equals("],")) {
                if (nestingDepth == 0) {
                    annotations[i] = LineType.STRUCTURAL;
                    continue;
                }
            }

            if (trimmed.startsWith("\"")) {
                String propertyName = extractPropertyName(trimmed);
                if (propertyName != null) {
                    if (focusSet.contains(propertyName.toLowerCase(Locale.ROOT))) {
                        currentFocusState = LineType.FOCUSED;
                        stateSetAtDepth = nestingDepth;
                    } else if (nestingDepth <= stateSetAtDepth) {
                        currentFocusState = LineType.NON_FOCUSED;
                        stateSetAtDepth = nestingDepth;
                    }
                    // else: deeper non-matching property inherits parent focus state
                }
            }

            annotations[i] = currentFocusState;
            nestingDepth += countNestingChange(trimmed);
        }
        return annotations;
    }

    private static String extractPropertyName(String trimmedLine) {
        if (!trimmedLine.startsWith("\"")) {
            return null;
        }
        int endQuote = trimmedLine.indexOf('"', 1);
        if (endQuote <= 1) {
            return null;
        }
        return trimmedLine.substring(1, endQuote);
    }

    private static int countNestingChange(String trimmedLine) {
        int change = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < trimmedLine.length(); i++) {
            char c = trimmedLine.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{' || c == '[') {
                change++;
            } else if (c == '}' || c == ']') {
                change--;
            }
        }
        return change;
    }

    private static String buildFormattedOutput(String[] lines, LineType[] annotations,
                                               Set<FocusEmphasis> emphasis, Set<FocusDeEmphasis> deEmphasis) {
        List<String> out = new ArrayList<>(lines.length);
        for (int i = 0; i < lines.length; i++) {
            switch (annotations[i]) {
                case STRUCTURAL -> out.add(lines[i]);
                case FOCUSED -> out.add(applyEmphasis(lines[i], emphasis));
                case NON_FOCUSED -> out.add(applyDeEmphasis(lines[i], deEmphasis));
                default -> out.add(lines[i]);
            }
        }
        return String.join("\n", out);
    }

    private static String buildHiddenOutput(String[] lines, LineType[] annotations,
                                            Set<FocusEmphasis> emphasis) {
        List<String> out = new ArrayList<>();
        boolean lastWasEllipsis = false;
        for (int i = 0; i < lines.length; i++) {
            switch (annotations[i]) {
                case STRUCTURAL -> {
                    out.add(lines[i]);
                    lastWasEllipsis = false;
                }
                case FOCUSED -> {
                    out.add(applyEmphasis(removeTrailingCommaIfNextIsHidden(lines[i], i, annotations), emphasis));
                    lastWasEllipsis = false;
                }
                case NON_FOCUSED -> {
                    if (!lastWasEllipsis) {
                        out.add("  ...");
                        lastWasEllipsis = true;
                    }
                }
                default -> { }
            }
        }
        return String.join("\n", out);
    }

    private static String removeTrailingCommaIfNextIsHidden(String line, int index, LineType[] annotations) {
        for (int j = index + 1; j < annotations.length; j++) {
            if (annotations[j] == LineType.STRUCTURAL) {
                continue;
            }
            if (annotations[j] == LineType.NON_FOCUSED) {
                String trimmed = line.stripTrailing();
                if (trimmed.endsWith(",")) {
                    return line.substring(0, line.lastIndexOf(','));
                }
            }
            break;
        }
        return line;
    }

    private static String applyEmphasis(String line, Set<FocusEmphasis> emphasis) {
        if (emphasis.isEmpty()) { // FocusEmphasis.None
            return line;
        }
        String indent = getIndent(line);
        String content = line.substring(indent.length());
        if (emphasis.contains(FocusEmphasis.COLORED)) {
            content = "<color:blue>" + content + "</color>";
        }
        if (emphasis.contains(FocusEmphasis.BOLD)) {
            content = "<b>" + content + "</b>";
        }
        return indent + content;
    }

    private static String applyDeEmphasis(String line, Set<FocusDeEmphasis> deEmphasis) {
        if (deEmphasis.isEmpty()) { // FocusDeEmphasis.None
            return line;
        }
        String indent = getIndent(line);
        String content = line.substring(indent.length());
        if (deEmphasis.contains(FocusDeEmphasis.SMALLER_TEXT)) {
            content = "<size:9>" + content + "</size>";
        }
        if (deEmphasis.contains(FocusDeEmphasis.LIGHT_GRAY)) {
            content = "<color:lightgray>" + content + "</color>";
        }
        return indent + content;
    }

    private static String getIndent(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return line.substring(0, i);
    }

    private static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }
}
