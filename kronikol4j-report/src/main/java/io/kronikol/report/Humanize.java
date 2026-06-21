package io.kronikol.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Port of the .NET {@code StringCasing.Titleize} (Humanizer) and
 * {@code ScenarioTitleResolver.FormatScenarioDisplayName} used to render parameterized-group headers
 * and the group display name. Kept byte-faithful to the .NET output (verified by the golden tests).
 */
final class Humanize {

    private static final Pattern PASCAL_CASE_WORD_PARTS = Pattern.compile(
        "(\\p{Lu}?\\p{Ll}+|[0-9]+\\p{Ll}*|\\p{Lu}+(?=\\p{Lu}|[0-9]|\\b)|\\p{Lo}+)[,;]?");
    private static final Pattern LOWER_TO_UPPER = Pattern.compile("(\\p{Ll})(\\p{Lu})");
    private static final Pattern UPPER_SEQUENCE = Pattern.compile("(\\p{Lu}+)(\\p{Lu}\\p{Ll})");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    private static final int MAX_PARAMETER_LENGTH = 200;

    private Humanize() {
    }

    /** Equivalent to Humanizer's {@code Titleize()} (Humanize + ToTitleCase). */
    static String titleize(String input) {
        String humanized = humanize(input);
        return humanized.isEmpty() ? input : toTitleCase(humanized);
    }

    /** Port of {@code ScenarioTitleResolver.FormatScenarioDisplayName}. */
    static String formatScenarioDisplayName(String testDisplayName) {
        String methodPath;
        String parameters = null;

        int parenIndex = testDisplayName.indexOf('(');
        if (parenIndex >= 0) {
            methodPath = testDisplayName.substring(0, parenIndex);
            String paramContent = trimEnd(testDisplayName.substring(parenIndex + 1), ')');
            if (!paramContent.isEmpty()) {
                parameters = paramContent.length() > MAX_PARAMETER_LENGTH
                    ? paramContent.substring(0, MAX_PARAMETER_LENGTH) + "…"
                    : paramContent;
            }
        } else {
            methodPath = testDisplayName;
        }

        int lastDot = methodPath.lastIndexOf('.');
        String methodName = lastDot >= 0 ? methodPath.substring(lastDot + 1) : methodPath;

        String humanized = splitPascalCase(methodName).replace("_", " ");
        humanized = MULTIPLE_SPACES.matcher(humanized).replaceAll(" ").strip();
        humanized = Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1).toLowerCase(Locale.ROOT);

        return parameters != null ? humanized + " [" + parameters + "]" : humanized;
    }

    private static String splitPascalCase(String input) {
        String result = LOWER_TO_UPPER.matcher(input).replaceAll("$1 $2");
        result = UPPER_SEQUENCE.matcher(result).replaceAll("$1 $2");
        return result;
    }

    private static String humanize(String input) {
        if (allUpper(input)) {
            return input;
        }
        if (input.indexOf('_') >= 0 || input.indexOf('-') >= 0) {
            return fromPascalCase(input.replace('_', ' ').replace('-', ' '));
        }
        return fromPascalCase(input);
    }

    private static String fromPascalCase(String input) {
        Matcher m = PASCAL_CASE_WORD_PARTS.matcher(input);
        List<String> parts = new ArrayList<>();
        while (m.find()) {
            String value = m.group();
            boolean keep = allUpper(value)
                && (value.length() > 1 || (m.start() > 0 && input.charAt(m.start() - 1) == ' ') || value.equals("I"));
            parts.add(keep ? value : value.toLowerCase(Locale.ROOT));
        }
        String result = String.join(" ", parts);
        if (allSpaceOrUpper(result) && result.contains(" ")) {
            result = result.toLowerCase(Locale.ROOT);
        }
        return result.isEmpty() ? result
            : Character.toUpperCase(result.charAt(0)) + result.substring(1);
    }

    /** Port of {@code TextInfo.ToTitleCase}: capitalise each word, lower the rest, but leave
     *  all-uppercase words (acronyms) untouched; word boundaries are whitespace. */
    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                sb.append(c);
                i++;
                continue;
            }
            int start = i;
            while (i < s.length() && !Character.isWhitespace(s.charAt(i))) {
                i++;
            }
            String word = s.substring(start, i);
            if (allUpper(word)) {
                sb.append(word); // acronym
            } else {
                sb.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    private static boolean allUpper(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isUpperCase(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean allSpaceOrUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != ' ' && !Character.isUpperCase(c)) {
                return false;
            }
        }
        return true;
    }

    private static String trimEnd(String s, char ch) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == ch) {
            end--;
        }
        return s.substring(0, end);
    }
}
