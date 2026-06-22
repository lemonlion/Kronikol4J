package io.kronikol.diagram.plantuml;

/**
 * Pretty-prints GraphQL query strings with indentation for diagram notes — a faithful port of the .NET
 * {@code GraphQlQueryFormatter}. Braces control indentation depth; content inside parentheses stays inline.
 */
public final class GraphQlQueryFormatter {

    private static final int INDENT_SIZE = 2;

    private GraphQlQueryFormatter() {
    }

    public static String formatQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(query.length() * 2);
        int braceDepth = 0;
        int parenDepth = 0;
        int i = 0;
        boolean justOutputNewline = false;
        StringBuilder currentWord = new StringBuilder();
        boolean inSpreadContext = false;

        while (i < query.length()) {
            char c = query.charAt(i);

            // String literals: copy verbatim
            if (c == '"') {
                sb.append(c);
                i++;
                while (i < query.length()) {
                    sb.append(query.charAt(i));
                    if (query.charAt(i) == '\\' && i + 1 < query.length()) {
                        i++;
                        sb.append(query.charAt(i));
                    } else if (query.charAt(i) == '"') {
                        break;
                    }
                    i++;
                }
                i++;
                justOutputNewline = false;
                currentWord.setLength(0);
                continue;
            }

            // Inside parentheses: copy everything as-is
            if (parenDepth > 0) {
                sb.append(c);
                if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth--;
                }
                i++;
                justOutputNewline = false;
                continue;
            }

            if (c == '(') {
                parenDepth++;
                sb.append(c);
                i++;
                justOutputNewline = false;
                currentWord.setLength(0);
                continue;
            }

            // Open brace: increase depth, newline + indent
            if (c == '{') {
                braceDepth++;
                trimTrailingWhitespace(sb);
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append('{').append('\n');
                appendIndent(sb, braceDepth);
                i++;
                justOutputNewline = true;
                currentWord.setLength(0);
                inSpreadContext = false;
                continue;
            }

            // Close brace: decrease depth, newline + dedent
            if (c == '}') {
                braceDepth--;
                trimTrailingWhitespace(sb);
                sb.append('\n');
                appendIndent(sb, braceDepth);
                sb.append('}');
                i++;
                justOutputNewline = false;
                currentWord.setLength(0);
                inSpreadContext = false;
                continue;
            }

            // Whitespace handling
            if (Character.isWhitespace(c)) {
                while (i < query.length() && Character.isWhitespace(query.charAt(i))) {
                    i++;
                }
                if (justOutputNewline || sb.length() == 0) {
                    continue;
                }
                String word = currentWord.toString();
                currentWord.setLength(0);

                if (braceDepth > 0 && i < query.length() && query.charAt(i) == '@') {
                    sb.append(' '); // directive (@) stays attached to the previous field
                    continue;
                }

                if (braceDepth == 0) {
                    if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '}') {
                        sb.append("\n\n"); // double newline between top-level constructs (fragments)
                    } else {
                        sb.append(' ');
                    }
                } else if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '}') {
                    sb.append('\n'); // sibling after a closing brace inside a selection set
                    appendIndent(sb, braceDepth);
                    justOutputNewline = true;
                } else if (word.endsWith(":")) {
                    sb.append(' '); // alias stays on the same line
                } else if (word.equals("...")) {
                    inSpreadContext = true; // "... on Type" stays inline
                    sb.append(' ');
                } else if (inSpreadContext) {
                    sb.append(' ');
                } else if (word.isEmpty()) {
                    sb.append(' ');
                } else {
                    sb.append('\n'); // selection-item separator
                    appendIndent(sb, braceDepth);
                    justOutputNewline = true;
                    inSpreadContext = false;
                }
                continue;
            }

            // Regular character
            sb.append(c);
            currentWord.append(c);
            justOutputNewline = false;
            i++;
        }

        return sb.toString().stripTrailing();
    }

    private static void trimTrailingWhitespace(StringBuilder sb) {
        while (sb.length() > 0 && Character.isWhitespace(sb.charAt(sb.length() - 1))) {
            sb.setLength(sb.length() - 1);
        }
    }

    private static void appendIndent(StringBuilder sb, int depth) {
        sb.append(" ".repeat(depth * INDENT_SIZE));
    }
}
